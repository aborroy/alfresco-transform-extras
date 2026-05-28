package org.alfresco.transform.videothumb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
public class FfmpegService {

    @Value("${transform.ffmpeg.timeOffset:1}")
    private int defaultTimeOffset;

    /**
     * Extracts a single JPEG thumbnail frame from a video file using ffmpeg.
     *
     * @param inputFile  the video input file
     * @param timeOffset seconds from start at which to capture the frame (e.g. "1")
     * @return the JPEG thumbnail File
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the process is interrupted
     */
    public File extractThumbnail(File inputFile, String timeOffset) throws IOException, InterruptedException {
        int offsetSeconds;
        try {
            offsetSeconds = (timeOffset != null && !timeOffset.isBlank())
                    ? Integer.parseInt(timeOffset.trim())
                    : defaultTimeOffset;
        } catch (NumberFormatException e) {
            log.warn("Invalid timeOffset '{}', falling back to default {}", timeOffset, defaultTimeOffset);
            offsetSeconds = defaultTimeOffset;
        }

        String hhmmss = toHhMmSs(offsetSeconds);

        Path outputDir = Files.createTempDirectory("videothumb-out-");
        File outputFile = outputDir.resolve("output.jpg").toFile();

        log.debug("Running ffmpeg on {} at offset {}", inputFile, hhmmss);

        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-i", inputFile.getAbsolutePath(),
                "-ss", hhmmss,
                "-vframes", "1",
                "-f", "image2",
                outputFile.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("ffmpeg process failed with exit code " + exitCode + ": " + new String(output));
        }

        if (!outputFile.exists() || outputFile.length() == 0) {
            throw new IOException("ffmpeg did not produce an output thumbnail at " + outputFile);
        }

        log.debug("ffmpeg produced thumbnail: {}", outputFile);
        return outputFile;
    }

    /**
     * Converts an offset in seconds to HH:MM:SS format.
     */
    private String toHhMmSs(int totalSeconds) {
        int hours   = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
