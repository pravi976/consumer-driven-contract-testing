package com.fedex.cdc.automation.generator;

import com.fedex.cdc.automation.model.ConsumerInteractionDefinition;
import com.fedex.cdc.automation.model.ConsumerMessageDefinition;
import com.fedex.cdc.automation.scanner.ConsumerPactScanner;
import com.fedex.cdc.automation.writer.ConsumerDrivenPactWriter;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class ConsumerDrivenPactGenerator {
    private final ApplicationContext context;
    private final ConsumerPactScanner scanner;
    private final ConsumerDrivenPactWriter writer;

    public ConsumerDrivenPactGenerator(ApplicationContext context, ConsumerPactScanner scanner, ConsumerDrivenPactWriter writer) {
        this.context = context;
        this.scanner = scanner;
        this.writer = writer;
    }

    public List<Path> generate(String outputDir) {
        return generate(outputDir, PactGenerationFilter.all());
    }

    public List<Path> generate(String outputDir, PactGenerationFilter filter) {
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
}

