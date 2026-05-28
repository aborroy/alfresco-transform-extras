package org.alfresco.transform.excel;

import lombok.RequiredArgsConstructor;
import org.alfresco.transform.base.TransformEngine;
import org.alfresco.transform.base.probes.ProbeTransform;
import org.alfresco.transform.config.TransformConfig;
import org.alfresco.transform.config.reader.TransformConfigResourceReader;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ExcelEngine implements TransformEngine {

    private static final String ENGINE_NAME = "excel";
    private static final String CONFIG_PATH = "classpath:excel_engine_config.json";

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
        // NOTE: engines/excel/src/main/resources/sample.xlsx must be a real XLSX binary
        // for this probe to function correctly at runtime.
        return new ProbeTransform(
                "sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "alfresco-metadata-extract",
                Map.of(),
                30, 8, 200, 5120, 900, 460);
    }
}
