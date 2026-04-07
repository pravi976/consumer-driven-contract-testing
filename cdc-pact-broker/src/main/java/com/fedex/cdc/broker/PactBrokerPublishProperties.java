package com.fedex.cdc.broker;

public record PactBrokerPublishProperties(
        String brokerUrl,
        String token,
        String providerVersion,
        String pactDirectory) {
}
