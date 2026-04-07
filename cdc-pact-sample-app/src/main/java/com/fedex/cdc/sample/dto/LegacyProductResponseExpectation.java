package com.fedex.cdc.sample.dto;

import com.fedex.cdc.automation.annotations.PactExample;
import com.fedex.cdc.automation.annotations.PactSecret;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record LegacyProductResponseExpectation(
        @PactExample("11111111-1111-1111-1111-111111111111") UUID id,
        @PactExample("SKU-LAPTOP-001") String sku,
        @PactExample("Enterprise Laptop") String name,
        @PactExample("1299.95") BigDecimal amount,
        Map<String, String> metadata,
        @PactSecret String supplierSecret) {
}


