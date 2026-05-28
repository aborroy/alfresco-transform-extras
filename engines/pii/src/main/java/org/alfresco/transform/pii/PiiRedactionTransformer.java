package org.alfresco.transform.pii;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.transform.base.CustomTransformer;
import org.alfresco.transform.base.TransformManager;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

/**
 * Custom transformer that redacts PII from a PDF document using Microsoft Presidio.
 * <p>
 * Supported transform options:
 * <ul>
 *   <li>{@code entities} – comma-separated list of entity types to redact
 *       (default: {@code PERSON,PHONE_NUMBER,EMAIL_ADDRESS})</li>
 *   <li>{@code scoreThreshold} – minimum confidence threshold, 0.0–1.0
 *       (default: {@code 0.5})</li>
 *   <li>{@code label} – replacement string for redacted spans
 *       (default: {@code [REDACTED]})</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PiiRedactionTransformer implements CustomTransformer {

    private final PresidioService presidioService;

    @Override
    public String getTransformerName() {
        return "pdf-pii-redaction";
    }

    @Override
    public void transform(String sourceMimetype, InputStream inputStream,
                          String targetMimetype, OutputStream outputStream,
                          Map<String, String> transformOptions,
                          TransformManager transformManager) throws Exception {

        String entities = transformOptions.getOrDefault("entities", "PERSON,PHONE_NUMBER,EMAIL_ADDRESS");
        double scoreThreshold = parseDouble(transformOptions.getOrDefault("scoreThreshold", "0.5"), 0.5);
        String label = transformOptions.getOrDefault("label", "[REDACTED]");

        Path tempDir = Files.createTempDirectory("pii-transform-");
        File inputFile = tempDir.resolve("input.pdf").toFile();

        try {
            Files.copy(inputStream, inputFile.toPath());
            log.debug("PII redaction: wrote input to {}", inputFile.getAbsolutePath());

            File outputFile = presidioService.redact(inputFile, entities, scoreThreshold, label);

            Files.copy(outputFile.toPath(), outputStream);
            log.debug("PII redaction: streamed output from {}", outputFile.getAbsolutePath());
        } finally {
            deleteTempDir(tempDir);
        }
    }

    private double parseDouble(String value, double defaultValue) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid scoreThreshold value '{}', using default {}", value, defaultValue);
            return defaultValue;
        }
    }

    private void deleteTempDir(Path tempDir) {
        try (var stream = Files.walk(tempDir)) {
            stream.sorted(Comparator.reverseOrder())
                  .map(Path::toFile)
                  .forEach(File::delete);
        } catch (Exception e) {
            log.warn("Failed to clean up temp directory {}: {}", tempDir, e.getMessage());
        }
    }
}
