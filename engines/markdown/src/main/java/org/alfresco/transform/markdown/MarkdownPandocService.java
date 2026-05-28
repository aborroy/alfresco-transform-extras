package org.alfresco.transform.markdown;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class MarkdownPandocService {

    public static final String MIMETYPE_PDF = "application/pdf";

    @Value("${transform.pandoc.pdf.engine:xelatex}")
    private String pdfEngine;

    /**
     * Builds the pandoc command argument list for PDF output.
     *
     * @param inputFile  source Markdown file
     * @param outputFile target PDF file
     * @return list of command tokens ready for {@link ProcessBuilder}
     */
    public List<String> buildCommand(File inputFile, File outputFile) {
        List<String> command = new ArrayList<>();
        command.add("pandoc");
        command.add(inputFile.getAbsolutePath());
        command.add("-o");
        command.add(outputFile.getAbsolutePath());
        command.add("--standalone");
        command.add("--pdf-engine=" + pdfEngine);
        command.add("-V");
        command.add("geometry:margin=2.5cm");

        File luaFilter = new File("/app/table-borders.lua");
        if (luaFilter.exists()) {
            command.add("--lua-filter=" + luaFilter.getAbsolutePath());
        }

        return command;
    }

    /**
     * Converts the given Markdown input file to PDF via pandoc.
     *
     * @param inputFile source file (Markdown)
     * @return the generated PDF file inside a temporary directory
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the pandoc process is interrupted
     */
    public File convert(File inputFile) throws IOException, InterruptedException {
        File tempDir = Files.createTempDirectory("pandoc-markdown-output-").toFile();
        File outputFile = new File(tempDir, "output.pdf");

        List<String> command = buildCommand(inputFile, outputFile);
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
