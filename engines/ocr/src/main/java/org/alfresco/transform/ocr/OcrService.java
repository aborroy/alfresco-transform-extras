package org.alfresco.transform.ocr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;

/**
 * Service that invokes the {@code ocrmypdf} CLI to produce a searchable PDF.
 * <p>
 * The {@code --skip-text} flag instructs ocrmypdf to skip pages that already
 * contain selectable text, so the transform is idempotent.
 */
@Slf4j
@Service
public class OcrService {

    @Value("${transform.ocr.language:eng}")
    private String defaultLanguage;

    /**
     * Run ocrmypdf on the given input PDF and return the output PDF file.
     *
     * @param inputFile the source PDF file
     * @param language  the Tesseract language code (e.g. "eng", "deu")
     * @return the output PDF file produced by ocrmypdf
     * @throws Exception if the process fails or exits with a non-zero code
     */
    public File ocr(File inputFile, String language) throws Exception {
        String lang = (language != null && !language.isBlank()) ? language.trim() : defaultLanguage;

        Path outputPath = inputFile.toPath().getParent().resolve("ocr-output.pdf");
        File outputFile = outputPath.toFile();

        List<String> command = List.of(
                "ocrmypdf",
                "--skip-text",
                "-l", lang,
                inputFile.getAbsolutePath(),
                outputFile.getAbsolutePath()
        );

        log.info("Running ocrmypdf command: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // Consume and log process output
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("ocrmypdf: {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("ocrmypdf exited with code " + exitCode
                    + " for input: " + inputFile.getAbsolutePath());
        }

        log.info("ocrmypdf completed successfully. Output: {}", outputFile.getAbsolutePath());
        return outputFile;
    }
}
