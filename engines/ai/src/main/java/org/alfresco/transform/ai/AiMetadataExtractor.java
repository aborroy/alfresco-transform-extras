package org.alfresco.transform.ai;

import org.alfresco.transform.base.TransformManager;
import org.alfresco.transform.base.metadata.AbstractMetadataExtractorEmbedder;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiMetadataExtractor extends AbstractMetadataExtractorEmbedder {

    private static final String OPTION_FIELDS = "aiFields";
    private static final List<String> ALL_FIELDS = List.of("title", "description", "tags", "language");

    private final LlmService llmService;

    public AiMetadataExtractor(LlmService llmService) {
        super(Type.EXTRACTOR, LoggerFactory.getLogger(AiMetadataExtractor.class));
        this.llmService = llmService;
    }

    @Override
    public String getTransformerName() {
        return "ai-metadata";
    }

    @Override
    public Map<String, Serializable> extractMetadata(
            String sourceMimetype,
            InputStream inputStream,
            String targetMimetype,
            OutputStream outputStream,
            Map<String, String> transformOptions,
            TransformManager transformManager) throws Exception {

        String fieldsOption = transformOptions.getOrDefault(OPTION_FIELDS, "title,description,tags,language");
        List<String> fields = Arrays.stream(fieldsOption.split(","))
                .map(String::trim)
                .filter(f -> !f.isEmpty())
                .filter(ALL_FIELDS::contains)
                .toList();

        String text = llmService.extractText(inputStream, sourceMimetype);
        Map<String, Serializable> metadata = new HashMap<>();

        for (String field : fields) {
            switch (field) {
                case "title" -> metadata.put("ai.title",
                        llmService.call(
                                "You are a document title generator. Return only a short, descriptive document title (5-10 words). No other text, no quotes.",
                                text));
                case "description" -> metadata.put("ai.description",
                        llmService.call(
                                "You are a document summariser. Return only a single concise paragraph summarising the main content. No headings, no lists, no preamble.",
                                text));
                case "tags" -> metadata.put("ai.taggable",
                        llmService.call(
                                "You are a document tagger. Return only a comma-separated list of 5 to 10 topic keywords that describe this document's main themes. No other text, no explanations.",
                                text));
                case "language" -> metadata.put("ai.locale",
                        llmService.call(
                                "You are a language detector. Return only the ISO 639-1 two-letter language code of this text. Examples: en, es, fr, de, it, pt. Return only the code, nothing else.",
                                text).toLowerCase().trim());
            }
        }

        return metadata;
    }

    @Override
    public void embedMetadata(
            String sourceMimetype,
            InputStream inputStream,
            String targetMimetype,
            OutputStream outputStream,
            Map<String, String> transformOptions,
            TransformManager transformManager) throws Exception {
        inputStream.transferTo(outputStream);
    }
}
