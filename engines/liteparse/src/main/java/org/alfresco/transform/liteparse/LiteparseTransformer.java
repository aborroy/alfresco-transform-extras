package org.alfresco.transform.liteparse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.transform.base.CustomTransformer;
import org.alfresco.transform.base.TransformManager;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiteparseTransformer implements CustomTransformer {

    private final LiteparseService liteparseService;
    private final SpreadsheetService spreadsheetService;

    /**
     * Spreadsheet source types, which are read cell by cell rather than handed to LiteParse.
     *
     * <p>LiteParse reaches a spreadsheet through a LibreOffice page render, so columns that do not fit
     * the rendered page are lost: a five-column rate card came back with two. Reading cells keeps every
     * column and yields real Markdown tables, because the grid is already a table.</p>
     */
    private static final java.util.Set<String> SPREADSHEET_MIMETYPES = java.util.Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel");

    @Override
    public String getTransformerName() {
        return "liteparse";
    }

    @Override
    public void transform(String sourceMimetype, InputStream inputStream,
                          String targetMimetype, OutputStream outputStream,
                          Map<String, String> transformOptions,
                          TransformManager transformManager) throws Exception {

        String ext = extensionFor(sourceMimetype);
        Path workDir = Files.createTempDirectory("liteparse-");
        try {
            Path inputPath = workDir.resolve("input" + ext);
            Files.copy(inputStream, inputPath);

            log.debug("LiteparseTransformer: converting {} ({}) -> {}", inputPath, sourceMimetype, targetMimetype);

            File outputFile = SPREADSHEET_MIMETYPES.contains(sourceMimetype)
                    ? spreadsheetService.convert(inputPath.toFile(), targetMimetype)
                    : liteparseService.convert(inputPath.toFile(), targetMimetype);
            Files.copy(outputFile.toPath(), outputStream);
        } finally {
            deleteRecursively(workDir.toFile());
        }
    }

    private static String extensionFor(String mimetype) {
        return switch (mimetype) {
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"       -> ".xlsx";
            case "application/vnd.ms-excel"                                                 -> ".xls";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> ".pptx";
            case "application/msword"                                                       -> ".doc";
            default                                                                         -> ".pdf";
        };
    }

    private void deleteRecursively(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        f.delete();
    }
}
