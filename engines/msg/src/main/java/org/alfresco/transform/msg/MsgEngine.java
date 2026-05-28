package org.alfresco.transform.msg;

import lombok.RequiredArgsConstructor;
import org.alfresco.transform.base.TransformEngine;
import org.alfresco.transform.base.probes.ProbeTransform;
import org.alfresco.transform.config.TransformConfig;
import org.alfresco.transform.config.reader.TransformConfigResourceReader;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class MsgEngine implements TransformEngine {

    private static final String ENGINE_NAME = "msg";
    private static final String CONFIG_PATH = "classpath:msg_engine_config.json";

    private final TransformConfigResourceReader transformConfigResourceReader;

    @Override
    public String getTransformEngineName() {
        return ENGINE_NAME;
    }

    @Override
    public String getStartupMessage() {
        return "Startup " + ENGINE_NAME + "\nConverts MSG/EML email files to PDF using Pandoc.";
    }

    @Override
    public TransformConfig getTransformConfig() {
        return transformConfigResourceReader.read(CONFIG_PATH);
    }

    @Override
    public ProbeTransform getProbeTransform() {
        return new ProbeTransform(
                "sample.eml", "message/rfc822", "application/pdf",
                Map.of(), 60, 8, 400, 10240, 1801, 920);
    }
}
