package com.fedex.cdc.sample;

import com.fedex.cdc.automation.generator.PactGenerationFilter;
import com.fedex.cdc.testsupport.AbstractConsumerContractGenerationTest;

import java.util.Set;

class ConsumerDrivenAgainstH2SamplesTest extends AbstractConsumerContractGenerationTest {
    @Override
    protected String outputDirectory() {
        return "build/pacts/h2-targeted";
    }

    @Override
    protected PactGenerationFilter generationFilter() {
        return new PactGenerationFilter(
                Set.of("pact-rest-h2-sample", "pact-jms-h2-sample"),
                Set.of("REST", "JMS"));
    }
}