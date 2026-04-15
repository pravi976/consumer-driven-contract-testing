package com.fedex.cdc.automation.scanner;

import com.fedex.cdc.automation.annotations.ConsumerPactClient;
import com.fedex.cdc.automation.annotations.ConsumerPactInteraction;
import com.fedex.cdc.automation.annotations.ConsumerPactMessageInteraction;
import com.fedex.cdc.automation.model.ConsumerInteractionDefinition;
import com.fedex.cdc.automation.model.ConsumerMessageDefinition;
import com.fedex.cdc.automation.support.PactSampleFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ConsumerPactScanner {
    private final PactSampleFactory sampleFactory;
    private static final String AUTO_EXPECTATIONS_FLAG = "cdc.expectations.auto";

    public ConsumerPactScanner(PactSampleFactory sampleFactory) {
        this.sampleFactory = sampleFactory;
    }

    public List<ConsumerInteractionDefinition> scanRestInteractions(ApplicationContext context) {
        List<ConsumerInteractionDefinition> definitions = new ArrayList<>();
        for (String beanName : context.getBeanDefinitionNames()) {
            Object bean = context.getBean(beanName);
            Class<?> beanType = ClassUtils.getUserClass(bean.getClass());
            ConsumerPactClient client = beanType.getAnnotation(ConsumerPactClient.class);
            if (client == null) {
                continue;
            }
            for (Method method : beanType.getDeclaredMethods()) {
                ConsumerPactInteraction interaction = method.getAnnotation(ConsumerPactInteraction.class);
                if (interaction == null) {
                    continue;
                }
                definitions.add(new ConsumerInteractionDefinition(
                        client.consumer(), client.provider(), interaction.description(), interaction.providerState(),
                        interaction.method(), interaction.path(), interaction.responseStatus(),
                        keyValueMap(interaction.query()), keyValueMap(interaction.requestHeaders()), keyValueMap(interaction.responseHeaders()),
                        interaction.requestBody() == Void.class ? null : sampleFactory.sample(interaction.requestBody()),
                        sampleFactory.sample(interaction.responseBody())));
            }
        }
        if (isAutoExpectationsEnabled(context)) {
            definitions.addAll(scanAutoRestInteractions(context));
        }
        return deduplicateRest(definitions);
    }

    public List<ConsumerMessageDefinition> scanMessageInteractions(ApplicationContext context) {
        List<ConsumerMessageDefinition> definitions = new ArrayList<>();
        for (String beanName : context.getBeanDefinitionNames()) {
            Object bean = context.getBean(beanName);
            Class<?> beanType = ClassUtils.getUserClass(bean.getClass());
            ConsumerPactClient client = beanType.getAnnotation(ConsumerPactClient.class);
            if (client == null) continue;
            for (java.lang.reflect.Method method : beanType.getDeclaredMethods()) {
                ConsumerPactMessageInteraction interaction = method.getAnnotation(ConsumerPactMessageInteraction.class);
                if (interaction == null) continue;
                java.util.Map<String, String> metadata = keyValueMap(interaction.metadata());
                metadata.putIfAbsent("destination", interaction.destination());
                definitions.add(new ConsumerMessageDefinition(
                        client.consumer(), client.provider(), interaction.description(), interaction.providerState(),
                        interaction.destination(), metadata, sampleFactory.sample(interaction.messageBody())));
            }
        }
        if (isAutoExpectationsEnabled(context)) {
            definitions.addAll(scanAutoMessageInteractions(context));
        }
        return deduplicateMessages(definitions);
    }

    private boolean isAutoExpectationsEnabled(ApplicationContext context) {
        String fromProperty = System.getProperty(AUTO_EXPECTATIONS_FLAG);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return Boolean.parseBoolean(fromProperty);
        }
        String fromEnvironment = System.getenv("CDC_EXPECTATIONS_AUTO");
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return Boolean.parseBoolean(fromEnvironment);
        }
        return Boolean.parseBoolean(context.getEnvironment().getProperty(AUTO_EXPECTATIONS_FLAG, "false"));
    }

    private List<ConsumerInteractionDefinition> scanAutoRestInteractions(ApplicationContext context) {
        List<ConsumerInteractionDefinition> definitions = new ArrayList<>();
        String consumerName = resolveConsumerName(context);

        for (String beanName : context.getBeanDefinitionNames()) {
            Object bean = context.getBean(beanName);
            Class<?> beanType = ClassUtils.getUserClass(bean.getClass());
            for (Class<?> candidate : feignCandidates(beanType)) {
                Annotation feign = findAnnotation(candidate, "org.springframework.cloud.openfeign.FeignClient", "FeignClient");
                Annotation httpExchange = findAnnotation(candidate, "org.springframework.web.service.annotation.HttpExchange", "HttpExchange");
                if (feign == null && httpExchange == null) {
                    continue;
                }
                String provider = resolveRestProvider(feign, candidate);
                String classPath = firstNonBlank(
                        annotationString(feign, "path"),
                        annotationString(httpExchange, "url"),
                        annotationString(httpExchange, "value"),
                        classLevelPath(candidate),
                        "");
                for (Method method : candidate.getMethods()) {
                    HttpMapping mapping = methodMapping(method);
                    if (mapping == null) {
                        continue;
                    }
                    Type responseType = unwrapContainerType(method.getGenericReturnType());
                    if (responseType == null || responseType == Void.class || responseType == Void.TYPE) {
                        continue;
                    }
                    Type requestType = requestBodyType(method, mapping.method());
                    definitions.add(new ConsumerInteractionDefinition(
                            consumerName,
                            provider,
                            "auto rest expectation " + mapping.method() + " " + normalizePath(classPath, mapping.path()),
                            "",
                            mapping.method(),
                            normalizePath(classPath, mapping.path()),
                            mapping.defaultResponseStatus(),
                            Map.of(),
                            Map.of("Accept", "application/json"),
                            Map.of("Content-Type", "application/json"),
                            requestType == null ? null : sampleFactory.sample(requestType),
                            sampleFactory.sample(responseType)));
                }
            }
        }
        return definitions;
    }

    private String resolveRestProvider(Annotation feign, Class<?> candidate) {
        String fromFeign = firstNonBlank(
                annotationString(feign, "name"),
                annotationString(feign, "value"),
                annotationString(feign, "contextId"));
        if (fromFeign != null) {
            return fromFeign;
        }
        String configured = firstNonBlank(
                System.getProperty("cdc.rest.default.provider"),
                System.getenv("CDC_REST_DEFAULT_PROVIDER"));
        if (configured != null) {
            return configured;
        }
        String simple = candidate.getSimpleName()
                .replaceAll("Client$", "")
                .replaceAll("Api$", "");
        if (simple.isBlank()) {
            simple = "rest";
        }
        String normalized = simple.replaceAll("([a-z])([A-Z])", "$1-$2")
                .replaceAll("[^a-zA-Z0-9]+", "-")
                .toLowerCase();
        return normalized + "-provider";
    }

    private List<ConsumerMessageDefinition> scanAutoMessageInteractions(ApplicationContext context) {
        List<ConsumerMessageDefinition> definitions = new ArrayList<>();
        String consumerName = resolveConsumerName(context);

        for (String beanName : context.getBeanDefinitionNames()) {
            Object bean = context.getBean(beanName);
            Class<?> beanType = ClassUtils.getUserClass(bean.getClass());
            for (Method method : beanType.getDeclaredMethods()) {
                Annotation listener = findAnnotation(method, "org.springframework.jms.annotation.JmsListener", "JmsListener");
                if (listener == null) {
                    continue;
                }
                String destination = firstNonBlank(annotationString(listener, "destination"),
                        firstFromArray(annotationValue(listener, "value")),
                        "unknown.destination");
                Type bodyType = jmsPayloadType(method);
                if (bodyType == null) {
                    continue;
                }
                String provider = resolveJmsProvider(destination);
                Map<String, String> metadata = new LinkedHashMap<>();
                metadata.put("contentType", "application/json");
                metadata.put("destination", destination);
                definitions.add(new ConsumerMessageDefinition(
                        consumerName,
                        provider,
                        "auto jms expectation for " + destination,
                        "",
                        destination,
                        metadata,
                        sampleFactory.sample(bodyType)));

                String publishDestination = outboundJmsDestination(method);
                Type publishBodyType = outboundJmsPayloadType(method);
                if (publishDestination != null && publishBodyType != null) {
                    Map<String, String> publishMetadata = new LinkedHashMap<>();
                    publishMetadata.put("contentType", "application/json");
                    publishMetadata.put("destination", publishDestination);
                    definitions.add(new ConsumerMessageDefinition(
                            consumerName,
                            provider,
                            "auto jms publish expectation for " + publishDestination,
                            "",
                            publishDestination,
                            publishMetadata,
                            sampleFactory.sample(publishBodyType)));
                }
            }
        }
        return definitions;
    }

    private String resolveConsumerName(ApplicationContext context) {
        return firstNonBlank(
                System.getProperty("cdc.consumer.name"),
                System.getenv("CDC_CONSUMER_NAME"),
                context.getEnvironment().getProperty("cdc.consumer.name"),
                context.getEnvironment().getProperty("spring.application.name"),
                "consumer-app");
    }

    private String resolveJmsProvider(String destination) {
        String configured = firstNonBlank(
                System.getProperty("cdc.jms.default.provider"),
                System.getenv("CDC_JMS_DEFAULT_PROVIDER"));
        if (configured != null) {
            return configured;
        }
        String sanitized = destination.replaceAll("[^a-zA-Z0-9]+", "-").replaceAll("(^-|-$)", "");
        return sanitized.isBlank() ? "jms-provider" : sanitized + "-provider";
    }

    private List<Class<?>> feignCandidates(Class<?> beanType) {
        Set<Class<?>> candidates = new LinkedHashSet<>();
        candidates.add(beanType);
        for (Class<?> iface : beanType.getInterfaces()) {
            candidates.add(iface);
        }
        return new ArrayList<>(candidates);
    }

    private String classLevelPath(Class<?> candidate) {
        Annotation mapping = findAnyMappingAnnotation(candidate);
        if (mapping == null) {
            return "";
        }
        return firstNonBlank(annotationString(mapping, "path"),
                firstFromArray(annotationValue(mapping, "value")),
                "");
    }

    private HttpMapping methodMapping(Method method) {
        Annotation get = findAnnotation(method, null, "GetMapping");
        if (get != null) return new HttpMapping("GET", firstPath(get), 200);
        Annotation post = findAnnotation(method, null, "PostMapping");
        if (post != null) return new HttpMapping("POST", firstPath(post), 200);
        Annotation put = findAnnotation(method, null, "PutMapping");
        if (put != null) return new HttpMapping("PUT", firstPath(put), 200);
        Annotation patch = findAnnotation(method, null, "PatchMapping");
        if (patch != null) return new HttpMapping("PATCH", firstPath(patch), 200);
        Annotation delete = findAnnotation(method, null, "DeleteMapping");
        if (delete != null) return new HttpMapping("DELETE", firstPath(delete), 200);
        Annotation request = findAnnotation(method, "org.springframework.web.bind.annotation.RequestMapping", "RequestMapping");
        if (request != null) {
            String path = firstPath(request);
            String methodName = "GET";
            Object methodValue = annotationValue(request, "method");
            if (methodValue instanceof Object[] array && array.length > 0 && array[0] != null) {
                methodName = String.valueOf(array[0]);
            }
            return new HttpMapping(methodName, path, 200);
        }

        Annotation getExchange = findAnnotation(method, "org.springframework.web.service.annotation.GetExchange", "GetExchange");
        if (getExchange != null) return new HttpMapping("GET", firstPath(getExchange), 200);
        Annotation postExchange = findAnnotation(method, "org.springframework.web.service.annotation.PostExchange", "PostExchange");
        if (postExchange != null) return new HttpMapping("POST", firstPath(postExchange), 200);
        Annotation putExchange = findAnnotation(method, "org.springframework.web.service.annotation.PutExchange", "PutExchange");
        if (putExchange != null) return new HttpMapping("PUT", firstPath(putExchange), 200);
        Annotation patchExchange = findAnnotation(method, "org.springframework.web.service.annotation.PatchExchange", "PatchExchange");
        if (patchExchange != null) return new HttpMapping("PATCH", firstPath(patchExchange), 200);
        Annotation deleteExchange = findAnnotation(method, "org.springframework.web.service.annotation.DeleteExchange", "DeleteExchange");
        if (deleteExchange != null) return new HttpMapping("DELETE", firstPath(deleteExchange), 200);
        Annotation httpExchange = findAnnotation(method, "org.springframework.web.service.annotation.HttpExchange", "HttpExchange");
        if (httpExchange != null) {
            String methodName = firstNonBlank(annotationString(httpExchange, "method"), "GET");
            return new HttpMapping(methodName, firstPath(httpExchange), 200);
        }
        return null;
    }

    private String firstPath(Annotation annotation) {
        return firstNonBlank(annotationString(annotation, "path"),
                annotationString(annotation, "url"),
                firstFromArray(annotationValue(annotation, "value")),
                "");
    }

    private Type requestBodyType(Method method, String httpMethod) {
        for (Parameter parameter : method.getParameters()) {
            if (findAnnotation(parameter, "org.springframework.web.bind.annotation.RequestBody", "RequestBody") != null) {
                return parameter.getParameterizedType();
            }
        }
        if (!List.of("POST", "PUT", "PATCH").contains(httpMethod)) {
            return null;
        }
        for (Parameter parameter : method.getParameters()) {
            if (findAnnotation(parameter, "org.springframework.web.bind.annotation.RequestParam", "RequestParam") != null) {
                continue;
            }
            if (findAnnotation(parameter, "org.springframework.web.bind.annotation.PathVariable", "PathVariable") != null) {
                continue;
            }
            if (findAnnotation(parameter, "org.springframework.web.bind.annotation.RequestHeader", "RequestHeader") != null) {
                continue;
            }
            return parameter.getParameterizedType();
        }
        return null;
    }

    private Type jmsPayloadType(Method method) {
        for (Parameter parameter : method.getParameters()) {
            if (findAnnotation(parameter, "org.springframework.messaging.handler.annotation.Payload", "Payload") != null) {
                return parameter.getParameterizedType();
            }
        }
        for (Parameter parameter : method.getParameters()) {
            Class<?> type = parameter.getType();
            String pkg = type.getPackageName();
            if (pkg.startsWith("jakarta.jms") || pkg.startsWith("javax.jms")
                    || pkg.startsWith("org.springframework.jms")
                    || pkg.startsWith("org.springframework.messaging")
                    || pkg.startsWith("org.springframework.amqp")) {
                continue;
            }
            return parameter.getParameterizedType();
        }
        return null;
    }

    private String outboundJmsDestination(Method method) {
        Annotation sendTo = findAnnotation(method, "org.springframework.messaging.handler.annotation.SendTo", "SendTo");
        if (sendTo == null) {
            sendTo = findAnnotation(method, "org.springframework.jms.annotation.SendTo", "SendTo");
        }
        if (sendTo == null) {
            return null;
        }
        return firstNonBlank(
                firstFromArray(annotationValue(sendTo, "value")),
                annotationString(sendTo, "destination"));
    }

    private Type outboundJmsPayloadType(Method method) {
        Type returnType = unwrapContainerType(method.getGenericReturnType());
        if (returnType == null || returnType == Void.class || returnType == Void.TYPE) {
            return null;
        }
        if (returnType instanceof Class<?> typeClass) {
            String pkg = typeClass.getPackageName();
            if (pkg.startsWith("org.springframework.jms")
                    || pkg.startsWith("org.springframework.messaging")
                    || pkg.startsWith("jakarta.jms")
                    || pkg.startsWith("javax.jms")) {
                return null;
            }
        }
        return returnType;
    }

    private Type unwrapContainerType(Type returnType) {
        if (returnType instanceof ParameterizedType parameterizedType && parameterizedType.getRawType() instanceof Class<?> raw) {
            String rawName = raw.getName();
            if ("org.springframework.http.ResponseEntity".equals(rawName)
                    || "org.springframework.http.HttpEntity".equals(rawName)
                    || "java.util.Optional".equals(rawName)) {
                Type[] args = parameterizedType.getActualTypeArguments();
                return args.length > 0 ? args[0] : Object.class;
            }
        }
        return returnType;
    }

    private List<ConsumerInteractionDefinition> deduplicateRest(List<ConsumerInteractionDefinition> definitions) {
        Map<String, ConsumerInteractionDefinition> deduped = new LinkedHashMap<>();
        for (ConsumerInteractionDefinition definition : definitions) {
            String key = definition.consumer() + "|" + definition.provider() + "|" + definition.method() + "|" + definition.path();
            deduped.putIfAbsent(key, definition);
        }
        return new ArrayList<>(deduped.values());
    }

    private List<ConsumerMessageDefinition> deduplicateMessages(List<ConsumerMessageDefinition> definitions) {
        Map<String, ConsumerMessageDefinition> deduped = new LinkedHashMap<>();
        for (ConsumerMessageDefinition definition : definitions) {
            String key = definition.consumer() + "|" + definition.provider() + "|" + definition.destination();
            deduped.putIfAbsent(key, definition);
        }
        return new ArrayList<>(deduped.values());
    }

    private Annotation findAnyMappingAnnotation(AnnotatedElement element) {
        for (String simple : List.of("RequestMapping", "GetMapping", "PostMapping", "PutMapping", "PatchMapping", "DeleteMapping", "HttpExchange")) {
            Annotation annotation = findAnnotation(element, null, simple);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }

    private Annotation findAnnotation(AnnotatedElement element, String fqcn, String simpleName) {
        for (Annotation annotation : element.getAnnotations()) {
            if (fqcn != null && fqcn.equals(annotation.annotationType().getName())) {
                return annotation;
            }
            if (simpleName.equals(annotation.annotationType().getSimpleName())) {
                return annotation;
            }
        }
        return null;
    }

    private Object annotationValue(Annotation annotation, String name) {
        if (annotation == null) {
            return null;
        }
        try {
            return annotation.annotationType().getMethod(name).invoke(annotation);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private String annotationString(Annotation annotation, String name) {
        if (annotation == null) {
            return null;
        }
        Object value = annotationValue(annotation, name);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        if (value instanceof String[] array && array.length > 0 && array[0] != null && !array[0].isBlank()) {
            return array[0];
        }
        return null;
    }

    private String firstFromArray(Object value) {
        if (value instanceof String[] array && array.length > 0) {
            return array[0];
        }
        if (value instanceof Object[] array && array.length > 0 && array[0] != null) {
            return String.valueOf(array[0]);
        }
        return null;
    }

    private String normalizePath(String classPath, String methodPath) {
        String left = classPath == null ? "" : classPath.trim();
        String right = methodPath == null ? "" : methodPath.trim();
        if (left.isEmpty() && right.isEmpty()) {
            return "/";
        }
        String merged = (left + "/" + right).replaceAll("//+", "/");
        if (!merged.startsWith("/")) {
            merged = "/" + merged;
        }
        return merged;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private java.util.Map<String, String> keyValueMap(String[] pairs) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        for (String pair : pairs) {
            int index = pair.indexOf('=');
            if (index <= 0) continue;
            map.put(pair.substring(0, index), pair.substring(index + 1));
        }
        return map;
    }

    private record HttpMapping(String method, String path, int defaultResponseStatus) {
    }
}

