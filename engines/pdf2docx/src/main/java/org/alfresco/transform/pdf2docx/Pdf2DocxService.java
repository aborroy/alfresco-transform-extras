package org.alfresco.transform.pdf2docx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
public class Pdf2DocxService {

    /**
     * Converts a PDF file to DOCX using the pdf2docx Python library.
     *
     * @param inputFile the PDF input file
     * @return the converted .docx output File
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the process is interrupted
     */
    public File convert(File inputFile) throws IOException, InterruptedException {
        Path outputDir = Files.createTempDirectory("pdf2docx-out-");
        File outputFile = outputDir.resolve("output.docx").toFile();

        String inputPath  = inputFile.getAbsolutePath();
        String outputPath = outputFile.getAbsolutePath();

        String pythonScript = "from pdf2docx import Converter; cv=Converter(r'" + inputPath + "'); cv.convert(r'" + outputPath + "'); cv.close()";

        log.debug("Running pdf2docx: {} -> {}", inputPath, outputPath);

        ProcessBuilder pb = new ProcessBuilder("python3", "-c", pythonScript);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("pdf2docx process failed with exit code " + exitCode + ": " + new String(output));
        }

        if (!outputFile.exists() || outputFile.length() == 0) {
            throw new IOException("pdf2docx did not produce output at " + outputFile);
        }

        log.debug("pdf2docx produced: {}", outputFile);
        return outputFile;
    }
}
