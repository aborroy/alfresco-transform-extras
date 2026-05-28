package org.alfresco.transform.md2html;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@Service
@Slf4j
public class Md2HtmlService {

    /**
     * Converts a Markdown file to standalone HTML5 using pandoc.
     *
     * @param inputFile source Markdown file
     * @return the generated HTML file inside a temporary directory
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the pandoc process is interrupted
     */
    public File convert(File inputFile) throws IOException, InterruptedException {
        File tempDir = Files.createTempDirectory("pandoc-md2html-output-").toFile();
        File outputFile = new File(tempDir, "output.html");

        List<String> command = List.of(
                "pandoc",
                "-f", "markdown",
                "-t", "html5",
                "--standalone",
                inputFile.getAbsolutePath(),
                "-o", outputFile.getAbsolutePath()
        );
        log.debug("Running pandoc command: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String processOutput = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException(
                    "Pandoc process exited with code " + exitCode + ". Output: " + processOutput);
        }

        if (!outputFile.exists()) {
            throw new IOException("Pandoc did not produce output file: " + outputFile.getAbsolutePath());
        }

        return outputFile;
    }
}
