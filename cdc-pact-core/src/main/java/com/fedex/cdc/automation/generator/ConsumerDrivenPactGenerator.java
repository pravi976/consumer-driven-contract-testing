package com.fedex.cdc.automation.generator;

import com.fedex.cdc.automation.model.ConsumerInteractionDefinition;
import com.fedex.cdc.automation.model.ConsumerMessageDefinition;
import com.fedex.cdc.automation.scanner.ConsumerPactScanner;
import com.fedex.cdc.automation.support.BuildGeneratedModelScanner;
import com.fedex.cdc.automation.support.GradleOpenApiDesignScanner;
import com.fedex.cdc.automation.writer.ConsumerDrivenPactWriter;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Component
public class ConsumerDrivenPactGenerator {
    private final ApplicationContext context;
    private final ConsumerPactScanner scanner;
    private final ConsumerDrivenPactWriter writer;
    private final GradleOpenApiDesignScanner openApiDesignScanner;
    private final BuildGeneratedModelScanner generatedModelScanner;
    private static final Logger LOGGER = Logger.getLogger(ConsumerDrivenPactGenerator.class.getName());

    public ConsumerDrivenPactGenerator(ApplicationContext context,
                                       ConsumerPactScanner scanner,
                                       ConsumerDrivenPactWriter writer,
                                       GradleOpenApiDesignScanner openApiDesignScanner,
                                       BuildGeneratedModelScanner generatedModelScanner) {
        this.context = context;
        this.scanner = scanner;
        this.writer = writer;
        this.openApiDesignScanner = openApiDesignScanner;
        this.generatedModelScanner = generatedModelScanner;
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

        List<String> configuredPaths = resolveGeneratedModelPaths();
        BuildGeneratedModelScanner.GeneratedModelInsights models = generatedModelScanner.scan(projectDir, configuredPaths);
        if (models.detected()) {
            int sampleSize = Math.min(5, models.modelClasses().size());
            LOGGER.info(() -> "Detected " + models.modelClasses().size() + " generated model classes under "
                    + joinPaths(models.scannedRoots()) + ". Sample: "
                    + String.join(", ", models.modelClasses().subList(0, sampleSize)));
        } else {
            LOGGER.info(() -> "No generated model classes found under " + joinPaths(models.scannedRoots())
                    + ". If OpenAPI tasks run in a different phase/path, configure generation before CDC test execution.");
        }
    }

    private List<String> resolveGeneratedModelPaths() {
        String raw = firstNonBlank(
                System.getProperty("cdc.openapi.generated.path"),
                System.getenv("CDC_OPENAPI_GENERATED_PATH"));
        if (raw == null) {
            return List.of("build/generated");
        }
        List<String> paths = new ArrayList<>();
        for (String token : raw.split("[,;]")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                paths.add(trimmed);
            }
        }
        return paths.isEmpty() ? List.of("build/generated") : paths;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String joinPaths(List<Path> paths) {
        return paths.stream().map(Path::toString).reduce((left, right) -> left + ", " + right).orElse("build/generated");
    }
}

