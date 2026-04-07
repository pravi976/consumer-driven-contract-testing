package com.fedex.cdc.automation.model;

public record ConsumerInteractionDefinition(
        String consumer,
        String provider,
        String description,
        String providerState,
        String method,
        String path,
        int responseStatus,
        java.util.Map<String, String> query,
        java.util.Map<String, String> requestHeaders,
        java.util.Map<String, String> responseHeaders,
        Object requestBody,
        Object responseBody) {
}



