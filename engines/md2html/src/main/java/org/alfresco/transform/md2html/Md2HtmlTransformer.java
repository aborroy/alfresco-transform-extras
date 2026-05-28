package org.alfresco.transform.md2html;

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
import java.util.Comparator;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class Md2HtmlTransformer implements CustomTransformer {

    private final Md2HtmlService md2HtmlService;

    @Override
    public String getTransformerName() {
        return "md2html";
    }

    @Override
    public void transform(String sourceMimetype, InputStream inputStream,
                          String targetMimetype, OutputStream outputStream,
                          Map<String, String> transformOptions,
                          TransformManager transformManager) throws Exception {

        log.debug("md2html transform: sourceMimetype={}, targetMimetype={}", sourceMimetype, targetMimetype);

        Path tempInputDir = Files.createTempDirectory("md2html-input-");
        File inputFile = tempInputDir.resolve("input.md").toFile();

        try {
            Files.write(inputFile.toPath(), inputStream.readAllBytes());

            File outputFile = md2HtmlService.convert(inputFile);
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
