package com.fedex.cdc.testsupport;

import com.fedex.cdc.automation.generator.ConsumerDrivenPactGenerator;
import com.fedex.cdc.automation.generator.PactGenerationFilter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.List;

@SpringBootTest
public abstract class AbstractConsumerContractGenerationTest {
    @Autowired
    private ConsumerDrivenPactGenerator generator;

    @Test
    void generateConsumerDrivenContracts() {
        List<Path> generated = generator.generate(outputDirectory(), generationFilter());
        Assertions.assertFalse(generated.isEmpty(), "No consumer-driven Pact files were generated");
    }

    protected String outputDirectory() {
        return "build/pacts";
    }

    protected PactGenerationFilter generationFilter() {
        return PactGenerationFilter.all();
    }
}