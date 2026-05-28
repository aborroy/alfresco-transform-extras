package org.alfresco.transform.msg;

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
public class MsgTransformer implements CustomTransformer {

    private final MsgService msgService;

    @Override
    public String getTransformerName() {
        return "msg";
    }

    @Override
    public void transform(
            String sourceMimetype,
            InputStream inputStream,
            String targetMimetype,
            OutputStream outputStream,
            Map<String, String> transformOptions,
            TransformManager transformManager) throws Exception {

        String extension = "application/vnd.ms-outlook".equals(sourceMimetype) ? ".msg" : ".eml";
        File inputFile = File.createTempFile("msg-input-", extension);
        try {
            try (FileOutputStream fos = new FileOutputStream(inputFile)) {
                inputStream.transferTo(fos);
            }
            log.debug("Converting {} ({}) to PDF", inputFile.getName(), sourceMimetype);
            File pdfFile = msgService.convertToPdf(inputFile, sourceMimetype);
            try {
                Files.copy(pdfFile.toPath(), outputStream);
            } finally {
                pdfFile.delete();
            }
        } finally {
            inputFile.delete();
        }
    }
}
