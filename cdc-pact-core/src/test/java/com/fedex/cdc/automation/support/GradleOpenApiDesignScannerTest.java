package com.fedex.cdc.automation.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradleOpenApiDesignScannerTest {
    private final GradleOpenApiDesignScanner scanner = new GradleOpenApiDesignScanner();

    @Test
    void detectsOpenApiGeneratorSignalsInBuildGradle(@TempDir Path tempDir) throws Exception {
        Path buildGradle = tempDir.resolve("build.gradle");
        Files.writeString(buildGradle, """
                plugins {
                  id "org.openapi.generator" version "7.0.0"
                }

                swaggerList.each {
                  tasks.create("openApiGenerateSomething")
                }
                """);

        GradleOpenApiDesignScanner.OpenApiBuildInsights insights = scanner.scan(tempDir);

        assertTrue(insights.detected());
        assertTrue(insights.signals().stream().anyMatch(signal -> signal.contains("OpenAPI generator plugin")));
        assertTrue(insights.signals().stream().anyMatch(signal -> signal.contains("openApiGenerate")));
    }

    @Test
    void returnsNotDetectedWhenNoBuildSignals(@TempDir Path tempDir) throws Exception {
        Path buildGradle = tempDir.resolve("build.gradle");
        Files.writeString(buildGradle, """
                plugins { id "java" }
                """);

        GradleOpenApiDesignScanner.OpenApiBuildInsights insights = scanner.scan(tempDir);

        assertFalse(insights.detected());
    }
}
