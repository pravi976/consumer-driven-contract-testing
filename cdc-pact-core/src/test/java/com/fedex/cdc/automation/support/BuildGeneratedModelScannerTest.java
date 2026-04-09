package com.fedex.cdc.automation.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildGeneratedModelScannerTest {
    private final BuildGeneratedModelScanner scanner = new BuildGeneratedModelScanner();

    @Test
    void detectsGeneratedModelClassesUnderBuildGenerated(@TempDir Path tempDir) throws Exception {
        Path modelFile = tempDir.resolve("build/generated/src/main/java/com/fedex/demo/model/Shipment.java");
        Files.createDirectories(modelFile.getParent());
        Files.writeString(modelFile, """
                package com.fedex.demo.model;

                public class Shipment {}
                """);

        BuildGeneratedModelScanner.GeneratedModelInsights insights = scanner.scan(tempDir);

        assertTrue(insights.detected());
        assertEquals(1, insights.modelClasses().size());
        assertEquals("com.fedex.demo.model.Shipment", insights.modelClasses().get(0));
    }

    @Test
    void ignoresGeneratedFilesOutsideModelFolders(@TempDir Path tempDir) throws Exception {
        Path dtoFile = tempDir.resolve("build/generated/src/main/java/com/fedex/demo/dto/ShipmentDto.java");
        Files.createDirectories(dtoFile.getParent());
        Files.writeString(dtoFile, """
                package com.fedex.demo.dto;

                public class ShipmentDto {}
                """);

        BuildGeneratedModelScanner.GeneratedModelInsights insights = scanner.scan(tempDir);

        assertFalse(insights.detected());
    }
}
