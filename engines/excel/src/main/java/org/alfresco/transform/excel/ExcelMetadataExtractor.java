package org.alfresco.transform.excel;

import org.alfresco.transform.base.TransformManager;
import org.alfresco.transform.base.metadata.AbstractMetadataExtractorEmbedder;
import org.slf4j.LoggerFactory;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Component
public class ExcelMetadataExtractor extends AbstractMetadataExtractorEmbedder {

    public ExcelMetadataExtractor() {
        super(Type.EXTRACTOR, LoggerFactory.getLogger(ExcelMetadataExtractor.class));
    }

    @Override
    public String getTransformerName() {
        return "excelextract";
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

        // Buffer to a temp file so WorkbookFactory can seek
        Path tempDir = Files.createTempDirectory("excel-meta-");
        File tempFile = tempDir.resolve("input.xlsx").toFile();
        try {
            try (OutputStream out = new FileOutputStream(tempFile)) {
                inputStream.transferTo(out);
            }

            ZipSecureFile.setMinInflateRatio(0.001);

            try (Workbook workbook = WorkbookFactory.create(tempFile, null, true)) {
                if (workbook instanceof XSSFWorkbook xssfWorkbook) {
                    POIXMLProperties props = xssfWorkbook.getProperties();
                    POIXMLProperties.CoreProperties core = props.getCoreProperties();
                    putIfNotBlank(metadata, "excel.title", core.getTitle());
                    putIfNotBlank(metadata, "excel.author", core.getCreator());
                    putIfNotBlank(metadata, "excel.subject", core.getSubject());
                    putIfNotBlank(metadata, "excel.description", core.getDescription());
                    putIfNotBlank(metadata, "excel.keywords", core.getKeywords());
                    putIfNotBlank(metadata, "excel.lastModifiedByUser", core.getLastModifiedByUser());
                    putIfNotBlank(metadata, "excel.revision", core.getRevision());
                } else if (workbook instanceof HSSFWorkbook hssfWorkbook) {
                    SummaryInformation si = hssfWorkbook.getSummaryInformation();
                    if (si != null) {
                        putIfNotBlank(metadata, "excel.title", si.getTitle());
                        putIfNotBlank(metadata, "excel.author", si.getAuthor());
                        putIfNotBlank(metadata, "excel.subject", si.getSubject());
                        putIfNotBlank(metadata, "excel.description", si.getComments());
                        putIfNotBlank(metadata, "excel.keywords", si.getKeywords());
                        putIfNotBlank(metadata, "excel.lastModifiedByUser", si.getLastAuthor());
                        if (si.getRevNumber() != null) {
                            metadata.put("excel.revision", si.getRevNumber());
                        }
                    }
                }
            }
        } finally {
            tempFile.delete();
            tempDir.toFile().delete();
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

    private void putIfNotBlank(Map<String, Serializable> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value.trim());
        }
    }
}
