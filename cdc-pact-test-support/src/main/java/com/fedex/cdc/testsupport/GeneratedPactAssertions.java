package com.fedex.cdc.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class GeneratedPactAssertions {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private GeneratedPactAssertions() {
    }

    public static void assertPactFile(Path pactFile, String consumer, String provider, String interactionNode, int expectedCount) throws IOException {
        Assertions.assertTrue(Files.exists(pactFile), "Generated pact file is missing: " + pactFile);
        String content = Files.readString(pactFile);
        JsonNode pact = OBJECT_MAPPER.readTree(content);
        Assertions.assertEquals(consumer, pact.path("consumer").path("name").asText());
        Assertions.assertEquals(provider, pact.path("provider").path("name").asText());
        Assertions.assertEquals("3.0.0", pact.path("metadata").path("pactSpecification").path("version").asText());
        Assertions.assertEquals(expectedCount, pact.path(interactionNode).size());
        Assertions.assertTrue(content.contains("matchingRules"));
        Assertions.assertTrue(content.contains("********"));
        assertNoDuplicateDescriptions(pact.path(interactionNode));
    }

    private static void assertNoDuplicateDescriptions(JsonNode interactions) {
        Set<String> descriptions = new HashSet<>();
        for (JsonNode interaction : interactions) {
            String description = interaction.path("description").asText();
            Assertions.assertFalse(description.isBlank());
            Assertions.assertTrue(descriptions.add(description), "Duplicate generated contract interaction: " + description);
        }
    }
}
