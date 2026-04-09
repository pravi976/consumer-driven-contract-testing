package com.fedex.cdc.automation.support;

import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    @Test
    void usesSchemaExampleWhenPresent() {
        Map<?, ?> root = (Map<?, ?>) factory.sample(SchemaAnnotatedExpectation.class);
        assertEquals("TRACK-001", root.get("trackingNumber"));
        assertEquals(42, root.get("pieces"));
    }

    @Test
    void samplesOptionalAndCommonOpenApiDateTypes() {
        Map<?, ?> root = (Map<?, ?>) factory.sample(OpenApiTypeExpectation.class);
        assertTrue(root.get("maybeReference") instanceof String);
        assertTrue(root.get("readyAt") instanceof OffsetDateTime);
        assertTrue(root.get("docUri") instanceof URI);
        assertTrue(root.get("shipmentId") instanceof UUID);
    }

    record ItemExpectation(String sku, int quantity) {
    }

    record ComplexExpectation(
            List<ItemExpectation> items,
            Map<String, ItemExpectation> itemMap,
            Map<String, Integer> counters,
            double price) {
    }

    record SchemaAnnotatedExpectation(
            @Schema(example = "TRACK-001") String trackingNumber,
            @Schema(example = "42") int pieces) {
    }

    record OpenApiTypeExpectation(
            Optional<String> maybeReference,
            OffsetDateTime readyAt,
            URI docUri,
            UUID shipmentId) {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Schema {
        String example() default "";
    }
}
