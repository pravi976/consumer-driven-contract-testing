package com.fedex.cdc.sample.dto;

import com.fedex.cdc.automation.annotations.PactExample;
import com.fedex.cdc.automation.annotations.PactSecret;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record CustomerResponseExpectation(
        @PactExample("22222222-2222-2222-2222-222222222222") UUID id,
        @PactExample("Priya Customer") String name,
        @PactSecret String email,
        @PactExample("GOLD") String tier,
        LocalDate joinedOn,
        Map<String, String> links) {
}


