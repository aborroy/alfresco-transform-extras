package org.alfresco.transform.whisper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

@Slf4j
@Service
public class WhisperService {

    @Value("${transform.whisper.model:base}")
    private String defaultModel;

    /**
     * Transcribes an audio/video file using the OpenAI Whisper CLI.
     *
     * @param inputFile the audio or video file to transcribe
     * @param model     the Whisper model to use (e.g. "base", "small", "medium", "large")
     * @return the .txt output file produced by Whisper
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the process is interrupted
     */
    public File transcribe(File inputFile, String model) throws IOException, InterruptedException {
        String effectiveModel = (model != null && !model.isBlank()) ? model : defaultModel;

        Path outputDir = Files.createTempDirectory("whisper-out-");
        log.debug("Running whisper on {} with model={}, outputDir={}", inputFile, effectiveModel, outputDir);

        ProcessBuilder pb = new ProcessBuilder(
                "whisper",
                inputFile.getAbsolutePath(),
                "--model", effectiveModel,
                "--output_format", "txt",
                "--output_dir", outputDir.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Consume stdout/stderr to prevent blocking
        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            String processOutput = new String(output);
            throw new IOException("Whisper process failed with exit code " + exitCode + ": " + processOutput);
        }

        // Whisper produces a file named <stem>.txt in the output directory
        String stem = getFileStem(inputFile.getName());
        File txtFile = outputDir.resolve(stem + ".txt").toFile();

        if (!txtFile.exists()) {
            // Fall back: look for any .txt file in the output dir
            File[] txtFiles = outputDir.toFile().listFiles(f -> f.getName().endsWith(".txt"));
            if (txtFiles == null || txtFiles.length == 0) {
                throw new IOException("Whisper did not produce a .txt output file in " + outputDir);
            }
            txtFile = txtFiles[0];
        }

        log.debug("Whisper produced transcript: {}", txtFile);
        return txtFile;
    }

    private String getFileStem(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot > 0) ? filename.substring(0, dot) : filename;
    }
}
