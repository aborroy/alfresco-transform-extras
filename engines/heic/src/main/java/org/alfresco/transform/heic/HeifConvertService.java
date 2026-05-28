package org.alfresco.transform.heic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
public class HeifConvertService {

    /**
     * Converts a HEIC/HEIF image file to JPEG or PNG using the heif-convert CLI tool.
     *
     * @param inputFile       the HEIC/HEIF input file
     * @param targetExtension the desired output extension, e.g. ".jpg" or ".png"
     * @return the converted output File
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the process is interrupted
     */
    public File convert(File inputFile, String targetExtension) throws IOException, InterruptedException {
        Path outputDir = Files.createTempDirectory("heic-out-");
        File outputFile = outputDir.resolve("output" + targetExtension).toFile();

        log.debug("Running heif-convert: {} -> {}", inputFile, outputFile);

        ProcessBuilder pb = new ProcessBuilder(
                "heif-convert",
                inputFile.getAbsolutePath(),
                outputFile.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("heif-convert failed with exit code " + exitCode + ": " + new String(output));
        }

        if (!outputFile.exists() || outputFile.length() == 0) {
            throw new IOException("heif-convert did not produce output at " + outputFile);
        }

        log.debug("heif-convert produced: {}", outputFile);
        return outputFile;
    }
}
