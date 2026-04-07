package com.fedex.cdc.sample.contracts.jms;

import com.fedex.cdc.automation.annotations.ConsumerPactClient;
import com.fedex.cdc.automation.annotations.ConsumerPactMessageInteraction;
import com.fedex.cdc.sample.dto.CustomerEventExpectation;
import org.springframework.stereotype.Component;

@Component
@ConsumerPactClient(consumer = "consumer-driven-contract-testing", provider = "pact-jms-h2-sample")
public class JmsH2ProviderExpectations {
    @ConsumerPactMessageInteraction(
            description = "consumer expects customer created event from JMS H2 provider",
            providerState = "customer event exists in H2 audit store",
            destination = "customer.events.v1",
            metadata = {"contentType=application/json", "eventType=CUSTOMER_CREATED"},
            messageBody = CustomerEventExpectation.class)
    public void consumeCustomerCreatedEvent() {
    }

    @ConsumerPactMessageInteraction(
            description = "consumer expects customer command acknowledgement from JMS H2 provider",
            providerState = "customer command is accepted",
            destination = "customer.commands.v1",
            metadata = {"contentType=application/json", "eventType=CUSTOMER_COMMAND"},
            messageBody = CustomerEventExpectation.class)
    public void consumeCustomerCommandAcknowledgement() {
    }
}


