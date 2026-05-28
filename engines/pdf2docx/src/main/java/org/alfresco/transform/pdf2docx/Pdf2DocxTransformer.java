package org.alfresco.transform.pdf2docx;

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
public class Pdf2DocxTransformer implements CustomTransformer {

    private final Pdf2DocxService pdf2DocxService;

    @Override
    public String getTransformerName() {
        return "pdf2docx";
    }

    @Override
    public void transform(String sourceMimetype, InputStream inputStream,
                          String targetMimetype, OutputStream outputStream,
                          Map<String, String> transformOptions,
                          TransformManager transformManager) throws Exception {

        Path workDir = Files.createTempDirectory("pdf2docx-");
        try {
            // Write input stream to a temp .pdf file
            Path inputPath = workDir.resolve("input.pdf");
            Files.copy(inputStream, inputPath);
            File inputFile = inputPath.toFile();

            log.debug("Pdf2DocxTransformer: converting {} ({}) -> {}", inputFile, sourceMimetype, targetMimetype);

            // Convert using pdf2docx Python library
            File docxFile = pdf2DocxService.convert(inputFile);

            // Stream DOCX output
            Files.copy(docxFile.toPath(), outputStream);

        } finally {
            deleteRecursively(workDir.toFile());
        }
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
