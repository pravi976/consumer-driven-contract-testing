package com.fedex.cdc.automation.writer;

import com.fedex.cdc.automation.model.ConsumerInteractionDefinition;
import com.fedex.cdc.automation.model.ConsumerMessageDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class ConsumerDrivenPactWriter {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public List<Path> write(List<ConsumerInteractionDefinition> restDefinitions,
                            List<ConsumerMessageDefinition> messageDefinitions,
                            String outputDir) {
        List<Path> files = new ArrayList<>();
        groupRest(restDefinitions).values().forEach(group -> files.add(writeRestGroup(group, outputDir)));
        groupMessages(messageDefinitions).values().forEach(group -> files.add(writeMessageGroup(group, outputDir)));
        return files;
    }

    private Map<String, List<ConsumerInteractionDefinition>> groupRest(List<ConsumerInteractionDefinition> definitions) {
        Map<String, List<ConsumerInteractionDefinition>> grouped = new LinkedHashMap<>();
        for (ConsumerInteractionDefinition definition : definitions) {
            grouped.computeIfAbsent(definition.consumer() + "|" + definition.provider(), key -> new ArrayList<>()).add(definition);
        }
        return grouped;
    }

    private Map<String, List<ConsumerMessageDefinition>> groupMessages(List<ConsumerMessageDefinition> definitions) {
        Map<String, List<ConsumerMessageDefinition>> grouped = new LinkedHashMap<>();
        for (ConsumerMessageDefinition definition : definitions) {
            grouped.computeIfAbsent(definition.consumer() + "|" + definition.provider(), key -> new ArrayList<>()).add(definition);
        }
        return grouped;
    }

    private Path writeRestGroup(List<ConsumerInteractionDefinition> interactions, String outputDir) {
        ConsumerInteractionDefinition first = interactions.get(0);
        try {
            Path directory = Path.of(outputDir);
            Files.createDirectories(directory);
            Path file = directory.resolve(first.consumer() + "-" + first.provider() + "-consumer-driven-rest.json");
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("consumer", Map.of("name", first.consumer()));
            root.put("provider", Map.of("name", first.provider()));
            root.put("interactions", interactions.stream().map(this::interactionMap).toList());
            root.put("metadata", metadata("REST"));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), root);
            return file;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write REST Pact file", ex);
        }
    }

    private Path writeMessageGroup(List<ConsumerMessageDefinition> messages, String outputDir) {
        ConsumerMessageDefinition first = messages.get(0);
        try {
            Path directory = Path.of(outputDir);
            Files.createDirectories(directory);
            Path file = directory.resolve(first.consumer() + "-" + first.provider() + "-consumer-driven-jms.json");
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("consumer", Map.of("name", first.consumer()));
            root.put("provider", Map.of("name", first.provider()));
            root.put("messages", messages.stream().map(this::messageMap).toList());
            root.put("metadata", metadata("JMS"));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), root);
            return file;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write message Pact file", ex);
        }
    }

    private Map<String, Object> metadata(String contractType) {
        return Map.of(
                "pactSpecification", Map.of("version", "3.0.0"),
                "framework", Map.of("name", "consumer-driven-contract-testing", "contractType", contractType));
    }

    private Map<String, Object> interactionMap(ConsumerInteractionDefinition definition) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("description", definition.description());
        map.put("providerStates", definition.providerState().isBlank() ? List.of() : List.of(Map.of("name", definition.providerState())));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", definition.method());
        request.put("path", definition.path());
        request.put("query", definition.query());
        request.put("headers", definition.requestHeaders());
        request.put("body", definition.requestBody());
        addMatchingRules(request, definition.requestBody());
        map.put("request", request);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", definition.responseStatus());
        response.put("headers", definition.responseHeaders());
        response.put("body", definition.responseBody());
        addMatchingRules(response, definition.responseBody());
        map.put("response", response);
        return map;
    }

    private Map<String, Object> messageMap(ConsumerMessageDefinition definition) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("description", definition.description());
        map.put("providerStates", definition.providerState().isBlank() ? List.of() : List.of(Map.of("name", definition.providerState())));
        map.put("contents", definition.body());
        map.put("metadata", definition.metadata());
        addMatchingRules(map, definition.body());
        return map;
    }

    private void addMatchingRules(Map<String, Object> target, Object body) {
        Map<String, Object> rules = new LinkedHashMap<>();
        collectRules(body, "$", rules);
        if (!rules.isEmpty()) target.put("matchingRules", Map.of("body", rules));
    }

    private void collectRules(Object value, String path, Map<String, Object> rules) {
        if (value == null) return;
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) collectRules(entry.getValue(), path + "." + entry.getKey(), rules);
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            rules.put(path, matcher(Map.of("match", "type", "min", 1)));
            int index = 0;
            for (Object item : iterable) collectRules(item, path + "[" + index++ + "]", rules);
            return;
        }
        if (value instanceof UUID) rules.put(path, matcher(Map.of("match", "regex", "regex", UUID_PATTERN.pattern())));
        else if (value instanceof Integer || value instanceof Long) rules.put(path, matcher(Map.of("match", "integer")));
        else if (value instanceof Number) rules.put(path, matcher(Map.of("match", "decimal")));
        else rules.put(path, matcher(Map.of("match", "type")));
    }

    private Map<String, Object> matcher(Map<String, Object> rule) {
        return Map.of("matchers", List.of(rule), "combine", "AND");
    }

    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
}

