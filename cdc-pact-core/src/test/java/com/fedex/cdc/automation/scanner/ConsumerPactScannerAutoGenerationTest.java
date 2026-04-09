package com.fedex.cdc.automation.scanner;

import com.fedex.cdc.automation.model.ConsumerInteractionDefinition;
import com.fedex.cdc.automation.model.ConsumerMessageDefinition;
import com.fedex.cdc.automation.support.PactSampleFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerPactScannerAutoGenerationTest {
    private static final String FLAG = "cdc.expectations.auto";

    @AfterEach
    void cleanup() {
        System.clearProperty(FLAG);
        System.clearProperty("cdc.consumer.name");
        System.clearProperty("cdc.jms.default.provider");
    }

    @Test
    void autoGeneratesRestAndJmsExpectationsWhenEnabled() {
        System.setProperty(FLAG, "true");
        System.setProperty("cdc.consumer.name", "auto-consumer");
        System.setProperty("cdc.jms.default.provider", "auto-jms-provider");

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(FeignClientBean.class);
            context.registerBean(JmsConsumerBean.class);
            context.refresh();

            ConsumerPactScanner scanner = new ConsumerPactScanner(new PactSampleFactory());

            List<ConsumerInteractionDefinition> rest = scanner.scanRestInteractions(context);
            List<ConsumerMessageDefinition> jms = scanner.scanMessageInteractions(context);

            assertEquals(1, rest.size());
            ConsumerInteractionDefinition interaction = rest.get(0);
            assertEquals("auto-consumer", interaction.consumer());
            assertEquals("pricing-provider", interaction.provider());
            assertEquals("POST", interaction.method());
            assertEquals("/api/shipments/quote", interaction.path());
            assertNotNull(interaction.requestBody());
            assertNotNull(interaction.responseBody());

            assertEquals(1, jms.size());
            ConsumerMessageDefinition message = jms.get(0);
            assertEquals("auto-consumer", message.consumer());
            assertEquals("auto-jms-provider", message.provider());
            assertEquals("shipment.quote.events", message.destination());
            assertFalse(message.metadata().isEmpty());
            assertTrue(message.metadata().containsKey("contentType"));
        }
    }

    @FeignClient(name = "pricing-provider", path = "/api/shipments")
    interface PricingClient {
        @PostMapping(path = "/quote")
        QuoteResponse quote(@RequestBody QuoteRequest request);
    }

    static class FeignClientBean implements PricingClient {
        @Override
        public QuoteResponse quote(QuoteRequest request) {
            return null;
        }
    }

    static class JmsConsumerBean {
        @JmsListener(destination = "shipment.quote.events")
        public void consume(@Payload QuoteEvent message) {
        }
    }

    record QuoteRequest(String origin, String destination) {
    }

    record QuoteResponse(String quoteId, double amount) {
    }

    record QuoteEvent(String quoteId, String status) {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface FeignClient {
        String name() default "";

        String value() default "";

        String contextId() default "";

        String path() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface RequestMapping {
        String[] path() default {};

        String[] value() default {};
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface PostMapping {
        String[] path() default {};

        String[] value() default {};
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface RequestBody {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface JmsListener {
        String destination() default "";

        String[] value() default {};
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface Payload {
    }
}
