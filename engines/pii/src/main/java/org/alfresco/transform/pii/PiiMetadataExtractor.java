package org.alfresco.transform.pii;

import org.alfresco.transform.base.TransformManager;
import org.alfresco.transform.base.metadata.AbstractMetadataExtractorEmbedder;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Component
public class PiiMetadataExtractor extends AbstractMetadataExtractorEmbedder {

    @Autowired
    private PresidioService presidioService;

    public PiiMetadataExtractor() {
        super(Type.EXTRACTOR, LoggerFactory.getLogger(PiiMetadataExtractor.class));
    }

    @Override
    public String getTransformerName() {
        return "pdf-pii-metadata";
    }

    @Override
    public Map<String, Serializable> extractMetadata(
            String sourceMimetype,
            InputStream inputStream,
            String targetMimetype,
            OutputStream outputStream,
            Map<String, String> transformOptions,
            TransformManager transformManager) throws Exception {

        Path tempDir = Files.createTempDirectory("pii-meta-");
        File sourceFile = tempDir.resolve("input.pdf").toFile();
        try {
            try (OutputStream out = new FileOutputStream(sourceFile)) {
                inputStream.transferTo(out);
            }
            String jsonOutput = presidioService.analyze(sourceFile);
            Map<String, String> analyzed = presidioService.parseAnalyzeResult(jsonOutput);
            Map<String, Serializable> metadata = new HashMap<>();
            analyzed.forEach(metadata::put);
            return metadata;
        } finally {
            sourceFile.delete();
            tempDir.toFile().delete();
        }
    }

    @Override
    public void embedMetadata(
            String sourceMimetype,
            InputStream inputStream,
            String targetMimetype,
            OutputStream outputStream,
            Map<String, String> transformOptions,
            TransformManager transformManager) throws Exception {
        // Embedding not supported — pass through
        inputStream.transferTo(outputStream);
    }
}
