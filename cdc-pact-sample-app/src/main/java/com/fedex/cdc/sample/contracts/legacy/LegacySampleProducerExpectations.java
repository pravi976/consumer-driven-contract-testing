package com.fedex.cdc.sample.contracts.legacy;

import com.fedex.cdc.automation.annotations.ConsumerPactClient;
import com.fedex.cdc.automation.annotations.ConsumerPactInteraction;
import com.fedex.cdc.sample.dto.LegacyProductResponseExpectation;
import org.springframework.stereotype.Component;

@Component
@ConsumerPactClient(consumer = "consumer-driven-contract-testing", provider = "sample-producer")
public class LegacySampleProducerExpectations {
    @ConsumerPactInteraction(
            description = "consumer still expects legacy sample product details when legacy profile is included",
            providerState = "legacy product exists",
            method = "GET",
            path = "/api/products/11111111-1111-1111-1111-111111111111",
            query = {"includeInventory=true"},
            responseBody = LegacyProductResponseExpectation.class)
    public void readLegacyProduct() {
    }
}


