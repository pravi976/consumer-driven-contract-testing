package com.fedex.cdc.automation.support;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class BuildGeneratedModelScanner {
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;\\s*$", Pattern.MULTILINE);

    public GeneratedModelInsights scan(Path projectDir) {
        Path generatedRoot = projectDir.resolve("build").resolve("generated");
        if (!Files.isDirectory(generatedRoot)) {
            return GeneratedModelInsights.notDetected(generatedRoot);
        }

        Set<String> models = new LinkedHashSet<>();
        try (Stream<Path> stream = Files.walk(generatedRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(this::isSourceFile)
                    .forEach(path -> maybeAddModelFqcn(path, models));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed scanning generated models under " + generatedRoot, ex);
        }

        return new GeneratedModelInsights(!models.isEmpty(), generatedRoot, List.copyOf(models));
    }

    private boolean isSourceFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".java") || name.endsWith(".kt");
    }

    private void maybeAddModelFqcn(Path source, Set<String> models) {
        String normalized = source.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (!normalized.contains("/model/")) {
            return;
        }

        String content;
        try {
            content = Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return;
        }

        Matcher matcher = PACKAGE_PATTERN.matcher(content);
        String simpleName = simpleClassName(source.getFileName().toString());
        if (simpleName == null) {
            return;
        }
        if (matcher.find()) {
            models.add(matcher.group(1) + "." + simpleName);
        } else {
            models.add(simpleName);
        }
    }

    private String simpleClassName(String fileName) {
        if (fileName.endsWith(".java")) {
            return fileName.substring(0, fileName.length() - ".java".length());
        }
        if (fileName.endsWith(".kt")) {
            return fileName.substring(0, fileName.length() - ".kt".length());
        }
        return null;
    }

    public record GeneratedModelInsights(boolean detected, Path generatedRoot, List<String> modelClasses) {
        static GeneratedModelInsights notDetected(Path generatedRoot) {
            return new GeneratedModelInsights(false, generatedRoot, new ArrayList<>());
        }
    }
}
