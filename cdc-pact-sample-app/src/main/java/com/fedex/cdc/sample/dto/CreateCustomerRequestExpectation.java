package com.fedex.cdc.sample.dto;

import com.fedex.cdc.automation.annotations.PactExample;
import com.fedex.cdc.automation.annotations.PactSecret;

public record CreateCustomerRequestExpectation(
        @PactExample("Priya Customer") String name,
        @PactSecret String email,
        @PactExample("GOLD") String tier) {
}


