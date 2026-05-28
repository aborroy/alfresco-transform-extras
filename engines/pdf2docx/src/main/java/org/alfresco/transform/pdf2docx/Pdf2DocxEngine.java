package org.alfresco.transform.pdf2docx;

import lombok.RequiredArgsConstructor;
import org.alfresco.transform.base.TransformEngine;
import org.alfresco.transform.base.probes.ProbeTransform;
import org.alfresco.transform.config.TransformConfig;
import org.alfresco.transform.config.reader.TransformConfigResourceReader;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class Pdf2DocxEngine implements TransformEngine {

    private static final String ENGINE_NAME = "pdf2docx";
    private static final String CONFIG_PATH = "classpath:pdf2docx_engine_config.json";

    private final TransformConfigResourceReader transformConfigResourceReader;

    @Override
    public String getTransformEngineName() {
        return ENGINE_NAME;
    }

    @Override
    public String getStartupMessage() {
        return "Startup " + ENGINE_NAME;
    }

    @Override
    public TransformConfig getTransformConfig() {
        return transformConfigResourceReader.read(CONFIG_PATH);
    }

    @Override
    public ProbeTransform getProbeTransform() {
        return new ProbeTransform("sample.pdf", "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", Map.of(),
                120, 16, 800, 20480, 3601, 1840);
    }
}
