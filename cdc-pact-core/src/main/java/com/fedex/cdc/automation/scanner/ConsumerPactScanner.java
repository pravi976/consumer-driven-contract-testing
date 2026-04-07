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

import java.util.ArrayList;
import java.util.List;

@Component
public class ConsumerPactScanner {
    private final PactSampleFactory sampleFactory;

    public ConsumerPactScanner(PactSampleFactory sampleFactory) {
        this.sampleFactory = sampleFactory;
    }

    public List<ConsumerInteractionDefinition> scanRestInteractions(ApplicationContext context) {
        List<ConsumerInteractionDefinition> definitions = new ArrayList<>();
        for (String beanName : context.getBeanDefinitionNames()) {
            Object bean = context.getBean(beanName);
            Class<?> beanType = ClassUtils.getUserClass(bean.getClass());
            ConsumerPactClient client = beanType.getAnnotation(ConsumerPactClient.class);
            if (client == null) continue;
            for (java.lang.reflect.Method method : beanType.getDeclaredMethods()) {
                ConsumerPactInteraction interaction = method.getAnnotation(ConsumerPactInteraction.class);
                if (interaction == null) continue;
                definitions.add(new ConsumerInteractionDefinition(
                        client.consumer(), client.provider(), interaction.description(), interaction.providerState(),
                        interaction.method(), interaction.path(), interaction.responseStatus(),
                        keyValueMap(interaction.query()), keyValueMap(interaction.requestHeaders()), keyValueMap(interaction.responseHeaders()),
                        interaction.requestBody() == Void.class ? null : sampleFactory.sample(interaction.requestBody()),
                        sampleFactory.sample(interaction.responseBody())));
            }
        }
        return definitions;
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
        return definitions;
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
}

