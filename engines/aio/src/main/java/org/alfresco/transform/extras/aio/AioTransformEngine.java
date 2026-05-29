package org.alfresco.transform.extras.aio;

import org.alfresco.transform.base.TransformEngine;
import org.alfresco.transform.base.probes.ProbeTransform;
import org.alfresco.transform.config.TransformConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class AioTransformEngine implements TransformEngine {

    private static final String ENGINE_NAME = "alfresco-transform-extras-aio";

    @Autowired
    private List<TransformEngine> transformEngines;

    @Override
    public String getTransformEngineName() {
        return ENGINE_NAME;
    }

    @Override
    public String getStartupMessage() {
        if (transformEngines == null) {
            return "Startup " + ENGINE_NAME;
        }
        return transformEngines.stream()
                .filter(e -> e != this)
                .map(TransformEngine::getStartupMessage)
                .collect(Collectors.joining("\n"));
    }

    @Override
    public TransformConfig getTransformConfig() {
        return null;
    }

    @Override
    public ProbeTransform getProbeTransform() {
        if (transformEngines == null) {
            return null;
        }
        return transformEngines.stream()
                .filter(e -> e != this)
                .map(TransformEngine::getProbeTransform)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
