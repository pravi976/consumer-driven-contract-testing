package com.fedex.cdc.automation.model;

public record ConsumerMessageDefinition(
        String consumer,
        String provider,
        String description,
        String providerState,
        String destination,
        java.util.Map<String, String> metadata,
        Object body) {
}



