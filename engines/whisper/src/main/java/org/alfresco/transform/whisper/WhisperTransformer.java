package org.alfresco.transform.whisper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.transform.base.CustomTransformer;
import org.alfresco.transform.base.TransformManager;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhisperTransformer implements CustomTransformer {

    private final WhisperService whisperService;

    @Override
    public String getTransformerName() {
        return "whisper";
    }

    @Override
    public void transform(String sourceMimetype, InputStream inputStream,
                          String targetMimetype, OutputStream outputStream,
                          Map<String, String> transformOptions,
                          TransformManager transformManager) throws Exception {

        String model = transformOptions.getOrDefault("model", "base");
        String extension = mimetypeToExtension(sourceMimetype);

        Path workDir = Files.createTempDirectory("whisper-");
        try {
            // Write input stream to a temp file with the correct extension
            Path inputPath = workDir.resolve("input" + extension);
            Files.copy(inputStream, inputPath);
            File inputFile = inputPath.toFile();

            log.debug("WhisperTransformer: transcribing {} ({}), model={}", inputFile, sourceMimetype, model);

            // Run Whisper
            File txtFile = whisperService.transcribe(inputFile, model);

            // Stream the transcript to the output
            Files.copy(txtFile.toPath(), outputStream);

        } finally {
            deleteRecursively(workDir.toFile());
        }
    }

    private String mimetypeToExtension(String mimetype) {
        if (mimetype == null) {
            return ".audio";
        }
        return switch (mimetype) {
            case "audio/mpeg"       -> ".mp3";
            case "audio/wav"        -> ".wav";
            case "audio/ogg"        -> ".ogg";
            case "audio/flac"       -> ".flac";
            case "video/mp4"        -> ".mp4";
            case "video/quicktime"  -> ".mov";
            case "video/x-msvideo" -> ".avi";
            case "video/x-matroska" -> ".mkv";
            default                 -> ".audio";
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
