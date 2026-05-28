package org.alfresco.transform.videothumb;

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

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoThumbTransformer implements CustomTransformer {

    private final FfmpegService ffmpegService;

    @Override
    public String getTransformerName() {
        return "videothumb";
    }

    @Override
    public void transform(String sourceMimetype, InputStream inputStream,
                          String targetMimetype, OutputStream outputStream,
                          Map<String, String> transformOptions,
                          TransformManager transformManager) throws Exception {

        String timeOffset = transformOptions.getOrDefault("timeOffset", "1");
        String extension  = mimetypeToExtension(sourceMimetype);

        Path workDir = Files.createTempDirectory("videothumb-");
        try {
            // Write input stream to a temp file with the correct extension
            Path inputPath = workDir.resolve("input" + extension);
            Files.copy(inputStream, inputPath);
            File inputFile = inputPath.toFile();

            log.debug("VideoThumbTransformer: extracting thumbnail from {} ({}), timeOffset={}", inputFile, sourceMimetype, timeOffset);

            // Extract thumbnail via ffmpeg
            File jpegFile = ffmpegService.extractThumbnail(inputFile, timeOffset);

            // Stream JPEG to output
            Files.copy(jpegFile.toPath(), outputStream);

        } finally {
            deleteRecursively(workDir.toFile());
        }
    }

    private String mimetypeToExtension(String mimetype) {
        if (mimetype == null) {
            return ".mp4";
        }
        return switch (mimetype) {
            case "video/mp4"        -> ".mp4";
            case "video/quicktime"  -> ".mov";
            case "video/x-msvideo" -> ".avi";
            case "video/x-matroska" -> ".mkv";
            case "video/webm"       -> ".webm";
            default                 -> ".mp4";
        };
    }

    private void deleteRecursively(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        f.delete();
    }
}
