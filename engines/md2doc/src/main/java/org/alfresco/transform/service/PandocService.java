package org.alfresco.transform.service;

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
public class PandocService {

    public static final String MIMETYPE_DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String MIMETYPE_PDF = "application/pdf";

    @Value("${transform.pandoc.toc.enabled:false}")
    private boolean defaultTocEnabled;

    @Value("${transform.pandoc.toc.depth:3}")
    private int defaultTocDepth;

    @Value("${transform.pandoc.pdf.engine:xelatex}")
    private String pdfEngine;

    /**
     * Builds the pandoc command argument list.
     *
     * @param inputFile   source file
     * @param outputFile  target file
     * @param tocEnabled  whether to include a table of contents
     * @param tocDepth    depth of the table of contents
     * @param targetMimetype the desired output MIME type
     * @return list of command tokens ready for {@link ProcessBuilder}
     */
    public List<String> buildCommand(File inputFile, File outputFile,
                                     boolean tocEnabled, int tocDepth,
                                     String targetMimetype) {
        List<String> command = new ArrayList<>();
        command.add("pandoc");
        command.add(inputFile.getAbsolutePath());
        command.add("-o");
        command.add(outputFile.getAbsolutePath());
        command.add("--standalone");

        if (tocEnabled) {
            command.add("--toc");
            command.add("--toc-depth=" + tocDepth);
        }

        if (MIMETYPE_PDF.equals(targetMimetype)) {
            command.add("--pdf-engine=" + pdfEngine);
            command.add("-V");
            command.add("geometry:margin=2.5cm");
            File luaFilter = new File("/app/table-borders.lua");
            if (luaFilter.exists()) {
                command.add("--lua-filter=" + luaFilter.getAbsolutePath());
            }
        } else if (MIMETYPE_DOCX.equals(targetMimetype)) {
            File referenceDoc = new File("/app/reference.docx");
            if (referenceDoc.exists()) {
                command.add("--reference-doc=" + referenceDoc.getAbsolutePath());
            }
        }

        return command;
    }

    /**
     * Converts the given input file to the desired format via pandoc.
     *
     * @param inputFile      source file (Markdown)
     * @param targetMimetype desired output MIME type
     * @param tocEnabled     whether to generate a table of contents
     * @param tocDepth       depth of the table of contents
     * @return the generated output file inside a temporary directory
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the pandoc process is interrupted
     */
    public File convert(File inputFile, String targetMimetype,
                        boolean tocEnabled, int tocDepth)
            throws IOException, InterruptedException {

        String extension = MIMETYPE_PDF.equals(targetMimetype) ? ".pdf" : ".docx";
        File tempDir = Files.createTempDirectory("pandoc-output-").toFile();
        File outputFile = new File(tempDir, "output" + extension);

        List<String> command = buildCommand(inputFile, outputFile, tocEnabled, tocDepth, targetMimetype);
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

    public boolean isDefaultTocEnabled() {
        return defaultTocEnabled;
    }

    public int getDefaultTocDepth() {
        return defaultTocDepth;
    }
}
