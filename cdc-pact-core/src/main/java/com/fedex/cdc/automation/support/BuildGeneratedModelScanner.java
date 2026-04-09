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
    private static final List<String> DEFAULT_PATHS = List.of("build/generated");

    public GeneratedModelInsights scan(Path projectDir) {
        return scan(projectDir, DEFAULT_PATHS);
    }

    public GeneratedModelInsights scan(Path projectDir, List<String> configuredPaths) {
        List<String> effectivePaths = sanitizePaths(configuredPaths);
        List<Path> scannedRoots = new ArrayList<>();
        Set<String> models = new LinkedHashSet<>();

        for (String configuredPath : effectivePaths) {
            Path generatedRoot = resolvePath(projectDir, configuredPath);
            scannedRoots.add(generatedRoot);
            if (!Files.isDirectory(generatedRoot)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(generatedRoot)) {
                stream.filter(Files::isRegularFile)
                        .filter(this::isSourceFile)
                        .forEach(path -> maybeAddModelFqcn(path, models));
            } catch (IOException ex) {
                throw new IllegalStateException("Failed scanning generated models under " + generatedRoot, ex);
            }
        }

        if (scannedRoots.isEmpty()) {
            scannedRoots.add(resolvePath(projectDir, DEFAULT_PATHS.get(0)));
        }
        return new GeneratedModelInsights(!models.isEmpty(), scannedRoots, List.copyOf(models));
    }

    private List<String> sanitizePaths(List<String> configuredPaths) {
        List<String> raw = (configuredPaths == null || configuredPaths.isEmpty()) ? DEFAULT_PATHS : configuredPaths;
        List<String> result = new ArrayList<>();
        for (String path : raw) {
            if (path == null) {
                continue;
            }
            String trimmed = path.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result.isEmpty() ? DEFAULT_PATHS : result;
    }

    private Path resolvePath(Path projectDir, String configuredPath) {
        Path candidate = Path.of(configuredPath);
        return candidate.isAbsolute() ? candidate : projectDir.resolve(candidate);
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

    public record GeneratedModelInsights(boolean detected, List<Path> scannedRoots, List<String> modelClasses) {
        static GeneratedModelInsights notDetected(Path generatedRoot) {
            return new GeneratedModelInsights(false, List.of(generatedRoot), new ArrayList<>());
        }
    }
}
