package com.wonhoone.misterworld.application.demo;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

@Component
public class DemoScenarioReader {
    private final ObjectMapper json;
    public DemoScenarioReader(ObjectMapper json) { this.json = json; }

    public DemoScenarioManifest read(String filename) {
        try {
            var file = Path.of(filename);
            if (!Files.isRegularFile(file) || !Files.isReadable(file)) throw new IllegalStateException();
            // Reuse the application's strict scalar/enum configuration; also reject trailing JSON.
            try (var input = Files.newInputStream(file)) {
                return json.readerFor(DemoScenarioManifest.class)
                        .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(input);
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Demo scenario must be a readable regular file containing valid schema JSON");
        }
    }
}
