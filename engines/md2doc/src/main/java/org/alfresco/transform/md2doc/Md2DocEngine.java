package org.alfresco.transform.md2doc;

import lombok.RequiredArgsConstructor;
import org.alfresco.transform.base.TransformEngine;
import org.alfresco.transform.base.probes.ProbeTransform;
import org.alfresco.transform.config.TransformConfig;
import org.alfresco.transform.config.reader.TransformConfigResourceReader;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class Md2DocEngine implements TransformEngine {

    private static final String ENGINE_NAME = "md2doc";
    private static final String CONFIG_PATH = "classpath:md2doc_engine_config.json";

    private final TransformConfigResourceReader transformConfigResourceReader;

    @Override
    public String getTransformEngineName() {
        return ENGINE_NAME;
    }

    @Override
    public String getStartupMessage() {
        return String.format("Startup %s%n  Converts Markdown to DOCX and PDF using Pandoc.", ENGINE_NAME);
    }

    @Override
    public TransformConfig getTransformConfig() {
        return transformConfigResourceReader.read(CONFIG_PATH);
    }

    @Override
    public ProbeTransform getProbeTransform() {
        return new ProbeTransform(
                "sample.md",
                "text/markdown",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                Map.of(),
                60, 16, 400, 10240, 1801, 920);
    }
}
