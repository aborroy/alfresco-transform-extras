package org.alfresco.transform.html2md;

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
public class Html2MdTransformer implements CustomTransformer {

    private final Html2MdService html2MdService;

    @Override
    public String getTransformerName() {
        return "html2md";
    }

    @Override
    public void transform(String sourceMimetype, InputStream inputStream,
                          String targetMimetype, OutputStream outputStream,
                          Map<String, String> transformOptions,
                          TransformManager transformManager) throws Exception {

        log.debug("html2md transform: sourceMimetype={}, targetMimetype={}", sourceMimetype, targetMimetype);

        Path tempInputDir = Files.createTempDirectory("html2md-input-");
        File inputFile = tempInputDir.resolve("input.html").toFile();

        try {
            Files.write(inputFile.toPath(), inputStream.readAllBytes());

            File outputFile = html2MdService.convert(inputFile);
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
