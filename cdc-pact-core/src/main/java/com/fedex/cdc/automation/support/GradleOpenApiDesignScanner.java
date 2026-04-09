package com.fedex.cdc.automation.support;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class GradleOpenApiDesignScanner {
    public OpenApiBuildInsights scan(Path projectDir) {
        List<Path> candidates = List.of(
                projectDir.resolve("build.gradle"),
                projectDir.resolve("build.gradle.kts"));
        for (Path candidate : candidates) {
            if (!Files.exists(candidate)) {
                continue;
            }
            String content = read(candidate);
            OpenApiBuildInsights insights = analyze(candidate, content);
            if (insights.detected()) {
                return insights;
            }
        }
        return OpenApiBuildInsights.notDetected();
    }

    private OpenApiBuildInsights analyze(Path buildFile, String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        List<String> signals = new ArrayList<>();
        if (lower.contains("org.openapi.generator") || lower.contains("openapi-generator")) {
            signals.add("OpenAPI generator plugin declaration");
        }
        if (lower.contains("openapigenerate")) {
            signals.add("openApiGenerate task usage");
        }
        if (lower.contains("swagger") || lower.contains("swaggerlist") || lower.contains("swaggersources")) {
            signals.add("swagger/openapi source iteration");
        }
        if (lower.contains("generatetask.class")) {
            signals.add("dynamic OpenAPI GenerateTask creation");
        }
        return signals.isEmpty()
                ? OpenApiBuildInsights.notDetected()
                : new OpenApiBuildInsights(true, buildFile, signals);
    }

    private String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read Gradle build file: " + path, ex);
        }
    }

    public record OpenApiBuildInsights(boolean detected, Path buildFile, List<String> signals) {
        static OpenApiBuildInsights notDetected() {
            return new OpenApiBuildInsights(false, null, List.of());
        }
    }
}
