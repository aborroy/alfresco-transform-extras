package org.alfresco.transform.xml;

import org.alfresco.transform.base.TransformManager;
import org.alfresco.transform.base.metadata.AbstractMetadataExtractorEmbedder;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

@Component
public class XmlMetadataExtractor extends AbstractMetadataExtractorEmbedder {

    public XmlMetadataExtractor() {
        super(Type.EXTRACTOR, LoggerFactory.getLogger(XmlMetadataExtractor.class));
    }

    @Override
    public String getTransformerName() {
        return "xmlextract";
    }

    @Override
    public Map<String, Serializable> extractMetadata(
            String sourceMimetype,
            InputStream inputStream,
            String targetMimetype,
            OutputStream outputStream,
            Map<String, String> transformOptions,
            TransformManager transformManager) throws Exception {

        Map<String, Serializable> metadata = new HashMap<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Disable external entity processing to prevent XXE attacks
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setNamespaceAware(true);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(inputStream);
        document.getDocumentElement().normalize();

        Element root = document.getDocumentElement();
        NodeList children = root.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String key = child.getLocalName() != null ? child.getLocalName() : child.getNodeName();
                String value = child.getTextContent();
                if (value != null && !value.isBlank()) {
                    metadata.put(key, value.trim());
                }
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
        // Embedding not supported — pass through
        inputStream.transferTo(outputStream);
    }
}
