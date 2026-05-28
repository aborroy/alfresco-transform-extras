package org.alfresco.transform.pii;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service that delegates PDF PII redaction and analysis to Python scripts
 * backed by Microsoft Presidio.
 */
@Slf4j
@Service
public class PresidioService {

    @Value("${transform.presidio.script.redact:/opt/presidio/redact.py}")
    private String redactScript;

    @Value("${transform.presidio.script.analyze:/opt/presidio/analyze.py}")
    private String analyzeScript;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Redact PII from the given PDF file and return the redacted output file.
     *
     * @param inputFile      the source PDF
     * @param entities       comma-separated list of entity types to redact
     * @param scoreThreshold minimum confidence score (0.0–1.0)
     * @param label          replacement text for redacted spans
     * @return the redacted output PDF file
     * @throws Exception if the Python process fails
     */
    public File redact(File inputFile, String entities, double scoreThreshold, String label) throws Exception {
        Path outputPath = inputFile.toPath().getParent().resolve("pii-redacted.pdf");
        File outputFile = outputPath.toFile();

        List<String> command = List.of(
                "python3",
                redactScript,
                "--input", inputFile.getAbsolutePath(),
                "--output", outputFile.getAbsolutePath(),
                "--entities", entities,
                "--threshold", String.valueOf(scoreThreshold),
                "--label", label
        );

        log.info("Running Presidio redact: {}", String.join(" ", command));
        runProcess(command, "presidio-redact");

        return outputFile;
    }

    /**
     * Analyse the given PDF file for PII and return the raw JSON output from
     * the analysis script.
     *
     * @param inputFile the source PDF
     * @return raw JSON string produced by the analyze script
     * @throws Exception if the Python process fails
     */
    public String analyze(File inputFile) throws Exception {
        List<String> command = List.of(
                "python3",
                analyzeScript,
                "--input", inputFile.getAbsolutePath()
        );

        log.info("Running Presidio analyze: {}", String.join(" ", command));
        return runProcess(command, "presidio-analyze");
    }

    /**
     * Parse the JSON output of the analyze script into a metadata-friendly
     * structure.
     * <p>
     * Expected JSON format (example):
     * <pre>
     * [{"entity_type": "PERSON", "score": 0.85}, {"entity_type": "EMAIL_ADDRESS", "score": 0.95}]
     * </pre>
     *
     * @param jsonOutput raw JSON string from the analyze script
     * @return map containing {@code pii.hasPII} and {@code pii.entities}
     */
    public Map<String, String> parseAnalyzeResult(String jsonOutput) {
        try {
            List<Map<String, Object>> results = objectMapper.readValue(
                    jsonOutput, new TypeReference<>() {});

            List<String> entityTypes = new ArrayList<>();
            for (Map<String, Object> result : results) {
                Object entityType = result.get("entity_type");
                if (entityType != null) {
                    entityTypes.add(entityType.toString());
                }
            }

            String hasPII = entityTypes.isEmpty() ? "false" : "true";
            String entitiesStr = String.join(",", entityTypes);

            return Map.of(
                    "pii.hasPII", hasPII,
                    "pii.entities", entitiesStr
            );
        } catch (Exception e) {
            log.warn("Failed to parse Presidio analyze JSON output: {}", e.getMessage());
            return Map.of("pii.hasPII", "false", "pii.entities", "");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String runProcess(List<String> command, String logPrefix) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
                log.debug("{}: {}", logPrefix, line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException(logPrefix + " exited with code " + exitCode
                    + ". Output: " + output);
        }

        return output.toString().trim();
    }
}
