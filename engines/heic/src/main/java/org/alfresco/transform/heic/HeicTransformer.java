package org.alfresco.transform.heic;

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
public class HeicTransformer implements CustomTransformer {

    private final HeifConvertService heifConvertService;

    @Override
    public String getTransformerName() {
        return "heic";
    }

    @Override
    public void transform(String sourceMimetype, InputStream inputStream,
                          String targetMimetype, OutputStream outputStream,
                          Map<String, String> transformOptions,
                          TransformManager transformManager) throws Exception {

        String targetExtension = mimetypeToExtension(targetMimetype);

        Path workDir = Files.createTempDirectory("heic-");
        try {
            // Write input stream to a temp .heic file
            Path inputPath = workDir.resolve("input.heic");
            Files.copy(inputStream, inputPath);
            File inputFile = inputPath.toFile();

            log.debug("HeicTransformer: converting {} ({}) -> {}", inputFile, sourceMimetype, targetMimetype);

            // Convert using heif-convert
            File outputFile = heifConvertService.convert(inputFile, targetExtension);

            // Stream converted image to output
            Files.copy(outputFile.toPath(), outputStream);

        } finally {
            deleteRecursively(workDir.toFile());
        }
    }

    private String mimetypeToExtension(String mimetype) {
        if (mimetype == null) {
            return ".jpg";
        }
        return switch (mimetype) {
            case "image/jpeg" -> ".jpg";
            case "image/png"  -> ".png";
            default           -> ".jpg";
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
