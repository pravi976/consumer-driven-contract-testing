package com.fedex.cdc.automation.generator;

import java.util.Set;

public record PactGenerationFilter(Set<String> providers, Set<String> contractTypes) {
    public static PactGenerationFilter all() {
        return new PactGenerationFilter(Set.of(), Set.of());
    }

    public boolean includesProvider(String provider) {
        return providers == null || providers.isEmpty() || providers.contains(provider);
    }

    public boolean includesType(String type) {
        return contractTypes == null || contractTypes.isEmpty() || contractTypes.contains(type);
    }
}



