package org.alfresco.transform.transformer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.transform.base.CustomTransformer;
import org.alfresco.transform.base.TransformManager;
import org.alfresco.transform.service.PandocService;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class Md2DocTransformer implements CustomTransformer {

    private final PandocService pandocService;

    @Override
    public String getTransformerName() {
        return "md2doc";
    }

    @Override
    public void transform(String sourceMimetype, InputStream inputStream,
                          String targetMimetype, OutputStream outputStream,
                          Map<String, String> transformOptions,
                          TransformManager transformManager) throws Exception {

        boolean tocEnabled = parseTocEnabled(transformOptions);
        int tocDepth = parseTocDepth(transformOptions);

        log.debug("md2doc transform: sourceMimetype={}, targetMimetype={}, tocEnabled={}, tocDepth={}",
                sourceMimetype, targetMimetype, tocEnabled, tocDepth);

        Path tempInputDir = Files.createTempDirectory("md2doc-input-");
        File inputFile = tempInputDir.resolve("input.md").toFile();

        try {
            Files.write(inputFile.toPath(), inputStream.readAllBytes());

            File outputFile = pandocService.convert(inputFile, targetMimetype, tocEnabled, tocDepth);
            File outputDir = outputFile.getParentFile();

            try {
                outputStream.write(Files.readAllBytes(outputFile.toPath()));
            } finally {
                deleteDirectory(outputDir.toPath());
            }
        } finally {
            deleteDirectory(tempInputDir);
        }
    }

    private boolean parseTocEnabled(Map<String, String> options) {
        String value = options.get("tocEnabled");
        if (value == null || value.isBlank()) {
            return pandocService.isDefaultTocEnabled();
        }
        return Boolean.parseBoolean(value.trim());
    }

    private int parseTocDepth(Map<String, String> options) {
        String value = options.get("tocDepth");
        if (value == null || value.isBlank()) {
            return pandocService.getDefaultTocDepth();
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid tocDepth value '{}', using default {}", value, pandocService.getDefaultTocDepth());
            return pandocService.getDefaultTocDepth();
        }
    }

    private void deleteDirectory(Path path) {
        try {
            if (Files.exists(path)) {
                Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (IOException e) {
            log.warn("Failed to delete temporary directory: {}", path, e);
        }
    }
}
