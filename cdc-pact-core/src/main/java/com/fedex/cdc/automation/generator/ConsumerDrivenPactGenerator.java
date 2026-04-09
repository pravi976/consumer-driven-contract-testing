package com.fedex.cdc.automation.generator;

import com.fedex.cdc.automation.model.ConsumerInteractionDefinition;
import com.fedex.cdc.automation.model.ConsumerMessageDefinition;
import com.fedex.cdc.automation.scanner.ConsumerPactScanner;
import com.fedex.cdc.automation.support.GradleOpenApiDesignScanner;
import com.fedex.cdc.automation.writer.ConsumerDrivenPactWriter;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

@Component
public class ConsumerDrivenPactGenerator {
    private final ApplicationContext context;
    private final ConsumerPactScanner scanner;
    private final ConsumerDrivenPactWriter writer;
    private final GradleOpenApiDesignScanner openApiDesignScanner;
    private static final Logger LOGGER = Logger.getLogger(ConsumerDrivenPactGenerator.class.getName());

    public ConsumerDrivenPactGenerator(ApplicationContext context,
                                       ConsumerPactScanner scanner,
                                       ConsumerDrivenPactWriter writer,
                                       GradleOpenApiDesignScanner openApiDesignScanner) {
        this.context = context;
        this.scanner = scanner;
        this.writer = writer;
        this.openApiDesignScanner = openApiDesignScanner;
    }

    public List<Path> generate(String outputDir) {
        return generate(outputDir, PactGenerationFilter.all());
    }

    public List<Path> generate(String outputDir, PactGenerationFilter filter) {
        logOpenApiGenerationDesignIfDetected();
        List<ConsumerInteractionDefinition> rest = scanner.scanRestInteractions(context).stream()
                .filter(definition -> filter.includesProvider(definition.provider()))
                .filter(definition -> filter.includesType("REST"))
                .toList();
        List<ConsumerMessageDefinition> messages = scanner.scanMessageInteractions(context).stream()
                .filter(definition -> filter.includesProvider(definition.provider()))
                .filter(definition -> filter.includesType("JMS"))
                .toList();
        return writer.write(rest, messages, outputDir);
    }

    private void logOpenApiGenerationDesignIfDetected() {
        Path projectDir = Path.of(System.getProperty("user.dir"));
        GradleOpenApiDesignScanner.OpenApiBuildInsights insights = openApiDesignScanner.scan(projectDir);
        if (!insights.detected()) {
            return;
        }
        LOGGER.info(() -> "Detected OpenAPI/Swagger code generation in " + insights.buildFile()
                + " via signals: " + String.join(", ", insights.signals())
                + ". Consumer-driven-contract-testing will generate Pact payloads from generated model types.");
    }
}

