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
        System.clearProperty("cdc.rest.default.provider");
    }

    @Test
    void autoGeneratesRestAndJmsExpectationsWhenEnabled() {
        System.setProperty(FLAG, "true");
        System.setProperty("cdc.consumer.name", "auto-consumer");
        System.setProperty("cdc.jms.default.provider", "auto-jms-provider");
        System.setProperty("cdc.rest.default.provider", "auto-rest-provider");

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(FeignClientBean.class);
            context.registerBean(WebClientProxyBean.class);
            context.registerBean(JmsConsumerBean.class);
            context.refresh();

            ConsumerPactScanner scanner = new ConsumerPactScanner(new PactSampleFactory());

            List<ConsumerInteractionDefinition> rest = scanner.scanRestInteractions(context);
            List<ConsumerMessageDefinition> jms = scanner.scanMessageInteractions(context);

            assertEquals(2, rest.size());

            ConsumerInteractionDefinition interaction = rest.stream()
                    .filter(it -> "pricing-provider".equals(it.provider()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("auto-consumer", interaction.consumer());
            assertEquals("pricing-provider", interaction.provider());
            assertEquals("POST", interaction.method());
            assertEquals("/api/shipments/quote", interaction.path());
            assertNotNull(interaction.requestBody());
            assertNotNull(interaction.responseBody());

            ConsumerInteractionDefinition webClientInteraction = rest.stream()
                    .filter(it -> "auto-rest-provider".equals(it.provider()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("GET", webClientInteraction.method());
            assertEquals("/api/webclient/status/{id}", webClientInteraction.path());
            assertNotNull(webClientInteraction.responseBody());

            assertEquals(2, jms.size());
            ConsumerMessageDefinition message = jms.stream()
                    .filter(it -> "shipment.quote.events".equals(it.destination()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("auto-consumer", message.consumer());
            assertEquals("auto-jms-provider", message.provider());
            assertEquals("shipment.quote.events", message.destination());
            assertFalse(message.metadata().isEmpty());
            assertTrue(message.metadata().containsKey("contentType"));

            ConsumerMessageDefinition outbound = jms.stream()
                    .filter(it -> "shipment.waypoint.requests".equals(it.destination()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("auto-consumer", outbound.consumer());
            assertEquals("auto-jms-provider", outbound.provider());
            assertEquals("shipment.waypoint.requests", outbound.destination());
            assertNotNull(outbound.body());
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

    @HttpExchange(url = "/api/webclient")
    interface TrackingWebClient {
        @GetExchange(url = "/status/{id}")
        TrackingStatus getStatus(String id);
    }

    static class WebClientProxyBean implements TrackingWebClient {
        @Override
        public TrackingStatus getStatus(String id) {
            return null;
        }
    }

    static class JmsConsumerBean {
        @JmsListener(destination = "shipment.quote.events")
        @SendTo("shipment.waypoint.requests")
        public WaypointEvent consume(@Payload QuoteEvent message) {
            return null;
        }
    }

    record QuoteRequest(String origin, String destination) {
    }

    record QuoteResponse(String quoteId, double amount) {
    }

    record QuoteEvent(String quoteId, String status) {
    }

    record TrackingStatus(String id, String state) {
    }

    record WaypointEvent(String quoteId, String waypointCode) {
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

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface SendTo {
        String[] value() default {};
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface HttpExchange {
        String url() default "";

        String value() default "";

        String method() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface GetExchange {
        String url() default "";

        String value() default "";
    }
}
