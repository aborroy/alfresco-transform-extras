package org.alfresco.transform.convert2md;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Slf4j
@Service
public class DoclingService {

    @Value("${transform.docling.language:english}")
    private String defaultLanguage;

    @Value("${transform.docling.image:placeholder}")
    private String defaultImage;

    /**
     * Convert a PDF file to Markdown using the Docling Python library.
     *
     * @param inputFile the source PDF file
     * @param language  document language hint (e.g. "english")
     * @param image     image handling mode (e.g. "placeholder")
     * @return a temporary Markdown File (caller must delete)
     */
    public File convert(File inputFile, String language, String image) throws IOException, InterruptedException {
        String resolvedLanguage = (language != null && !language.isBlank()) ? language : defaultLanguage;
        String resolvedImage = (image != null && !image.isBlank()) ? image : defaultImage;

        File outputFile = new File(inputFile.getParent(), "output.md");

        String inputPath = inputFile.getAbsolutePath().replace("\\", "\\\\").replace("'", "\\'");
        String outputPath = outputFile.getAbsolutePath().replace("\\", "\\\\").replace("'", "\\'");

        String script = String.format(
                "from docling.document_converter import DocumentConverter%n" +
                "converter = DocumentConverter()%n" +
                "result = converter.convert('%s')%n" +
                "with open('%s', 'w') as f:%n" +
                "    f.write(result.document.export_to_markdown())%n",
                inputPath,
                outputPath
        );

        log.debug("Running Docling conversion: language={}, image={}, input={}", resolvedLanguage, resolvedImage, inputFile.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(List.of("python3", "-c", script));
        pb.redirectErrorStream(true);
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            String output = new String(process.getInputStream().readAllBytes());
            throw new IOException("Docling python3 process failed with exit code " + exitCode + ": " + output);
        }

        if (!outputFile.exists()) {
            throw new IOException("Docling did not produce output file: " + outputFile.getAbsolutePath());
        }

        return outputFile;
    }
}
