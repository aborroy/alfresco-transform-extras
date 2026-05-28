package org.alfresco.transform.ocr;

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
import java.util.Map;

/**
 * Custom transformer that applies OCR to a PDF using ocrmypdf and streams
 * the resulting searchable PDF back through the transform pipeline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrTransformer implements CustomTransformer {

    private final OcrService ocrService;

    @Override
    public String getTransformerName() {
        return "ocr";
    }

    @Override
    public void transform(String sourceMimetype, InputStream inputStream,
                          String targetMimetype, OutputStream outputStream,
                          Map<String, String> transformOptions,
                          TransformManager transformManager) throws Exception {

        String language = transformOptions.getOrDefault("language", "eng");

        // Write incoming stream to a temp file so ocrmypdf can read it
        Path tempDir = Files.createTempDirectory("ocr-transform-");
        File inputFile = tempDir.resolve("input.pdf").toFile();

        try {
            Files.copy(inputStream, inputFile.toPath());
            log.debug("OCR transform: wrote input to {}", inputFile.getAbsolutePath());

            File outputFile = ocrService.ocr(inputFile, language);

            Files.copy(outputFile.toPath(), outputStream);
            log.debug("OCR transform: streamed output from {}", outputFile.getAbsolutePath());
        } finally {
            // Clean up temp directory and all its contents
            try (var stream = Files.walk(tempDir)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                      .map(Path::toFile)
                      .forEach(File::delete);
            } catch (Exception e) {
                log.warn("Failed to clean up temp directory {}: {}", tempDir, e.getMessage());
            }
        }
    }
}
