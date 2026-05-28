package org.alfresco.transform.convert2md;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.transform.base.CustomTransformer;
import org.alfresco.transform.base.TransformManager;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class Convert2MdTransformer implements CustomTransformer {

    private final DoclingService doclingService;

    @Override
    public String getTransformerName() {
        return "convert2md";
    }

    @Override
    public void transform(
            String sourceMimetype,
            InputStream inputStream,
            String targetMimetype,
            OutputStream outputStream,
            Map<String, String> transformOptions,
            TransformManager transformManager) throws Exception {

        String language = transformOptions.getOrDefault("language", "english");
        String image = transformOptions.getOrDefault("image", "placeholder");

        File inputFile = File.createTempFile("convert2md-input-", ".pdf");
        try {
            try (FileOutputStream fos = new FileOutputStream(inputFile)) {
                inputStream.transferTo(fos);
            }
            log.debug("Converting PDF to Markdown with language={}, image={}", language, image);
            File mdFile = doclingService.convert(inputFile, language, image);
            try {
                Files.copy(mdFile.toPath(), outputStream);
            } finally {
                mdFile.delete();
            }
        } finally {
            inputFile.delete();
        }
    }
}
