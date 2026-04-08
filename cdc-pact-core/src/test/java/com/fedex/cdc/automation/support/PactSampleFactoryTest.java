package com.fedex.cdc.automation.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PactSampleFactoryTest {
    private final PactSampleFactory factory = new PactSampleFactory();

    @Test
    void generatesNestedObjectsInsideListsAndMaps() {
        Object sample = factory.sample(ComplexExpectation.class);
        assertInstanceOf(Map.class, sample);

        Map<?, ?> root = (Map<?, ?>) sample;
        assertTrue(root.get("items") instanceof List<?>);
        assertTrue(root.get("itemMap") instanceof Map<?, ?>);

        List<?> items = (List<?>) root.get("items");
        assertFalse(items.isEmpty());
        assertTrue(items.get(0) instanceof Map<?, ?>, "List element should be nested object map, not a string placeholder");

        Map<?, ?> itemMap = (Map<?, ?>) root.get("itemMap");
        assertFalse(itemMap.isEmpty());
        Object mapValue = itemMap.values().iterator().next();
        assertTrue(mapValue instanceof Map<?, ?>, "Map value should be nested object map");

        Map<?, ?> counters = (Map<?, ?>) root.get("counters");
        assertFalse(counters.isEmpty());
        Object counterValue = counters.values().iterator().next();
        assertTrue(counterValue instanceof Integer, "Map<Integer> value should be numeric sample");
    }

    @Test
    void generatesDecimalForPrimitiveDouble() {
        Map<?, ?> root = (Map<?, ?>) factory.sample(ComplexExpectation.class);
        assertTrue(root.get("price") instanceof Double);
    }

    record ItemExpectation(String sku, int quantity) {
    }

    record ComplexExpectation(
            List<ItemExpectation> items,
            Map<String, ItemExpectation> itemMap,
            Map<String, Integer> counters,
            double price) {
    }
}