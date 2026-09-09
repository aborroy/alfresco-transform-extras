package org.alfresco.transform.liteparse;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a spreadsheet's cells directly instead of going through a page-rendered conversion.
 *
 * <p>Two reasons this path exists rather than handing spreadsheets to LiteParse:</p>
 *
 * <ul>
 *   <li><b>Correctness.</b> LiteParse reaches a spreadsheet through a LibreOffice page render and then
 *       extracts text by position, so any column that does not fit the rendered page width is silently
 *       lost. Measured on a five-column rate card, only the first two columns came back. Reading cells
 *       loses nothing.</li>
 *   <li><b>Structure.</b> A spreadsheet is already a table, so Markdown tables fall out of the cell
 *       grid with no layout guessing at all. LiteParse cannot do this: as of 2.0.4 it exposes no table
 *       model, only page text and positioned text runs.</li>
 * </ul>
 *
 * <p>Rows are grouped so that interleaved prose survives as prose: a run of consecutive rows with two
 * or more populated cells becomes one table, a row with a single populated cell becomes a paragraph,
 * and a blank row ends the current table. That matters for real spreadsheets, which routinely put
 * narrative text in column A above and below the data.</p>
 */
@Slf4j
@Service
public class SpreadsheetService {

    /** Minimum populated cells for a row to be treated as tabular rather than prose. */
    private static final int MIN_TABLE_COLUMNS = 2;

    public File convert(File inputFile, String targetMimetype) throws IOException {
        boolean markdown = "text/markdown".equals(targetMimetype);

        String rendered;
        try (Workbook workbook = WorkbookFactory.create(inputFile, null, true)) {
            rendered = render(workbook, markdown);
        }

        Path outputDir = Files.createTempDirectory("liteparse-out-");
        File outputFile = outputDir.resolve(markdown ? "output.md" : "output.txt").toFile();
        Files.writeString(outputFile.toPath(), rendered, StandardCharsets.UTF_8);

        log.debug("SpreadsheetService: {} -> {} ({} chars, {})",
                inputFile.getAbsolutePath(), outputFile.getAbsolutePath(), rendered.length(), targetMimetype);
        return outputFile;
    }

    private String render(Workbook workbook, boolean markdown) {
        DataFormatter formatter = new DataFormatter();
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

        List<String> out = new ArrayList<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            List<List<String>> rows = readRows(sheet, formatter, evaluator);
            if (rows.stream().allMatch(List::isEmpty)) {
                continue;
            }
            if (!out.isEmpty()) {
                out.add("");
            }
            // Sheet names are meaningful in a workbook, and a heading gives chunking a boundary.
            out.add(markdown ? "## " + sheet.getSheetName() : sheet.getSheetName());
            out.add("");
            out.addAll(renderRows(rows, markdown));
        }
        return String.join("\n", out).replaceAll("\n{3,}", "\n\n").strip() + "\n";
    }

    /** Cell values per row, trimmed of trailing empties. */
    private List<List<String>> readRows(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<List<String>> rows = new ArrayList<>();
        int lastRow = sheet.getLastRowNum();
        for (int r = sheet.getFirstRowNum(); r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                rows.add(List.of());
                continue;
            }
            List<String> values = new ArrayList<>();
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                values.add(cell == null ? "" : value(cell, formatter, evaluator));
            }
            while (!values.isEmpty() && values.get(values.size() - 1).isBlank()) {
                values.remove(values.size() - 1);
            }
            rows.add(values.stream().anyMatch(v -> !v.isBlank()) ? values : List.of());
        }
        return rows;
    }

    private String value(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        try {
            // Formatted rather than raw, so a rate reads "2,400" and a date reads as displayed.
            return formatter.formatCellValue(cell, evaluator).strip();
        } catch (RuntimeException e) {
            // A formula that cannot be evaluated must not fail the whole transform.
            return formatter.formatCellValue(cell).strip();
        }
    }

    /** Groups rows into tables and paragraphs, then renders each. */
    private List<String> renderRows(List<List<String>> rows, boolean markdown) {
        List<String> out = new ArrayList<>();
        List<List<String>> pending = new ArrayList<>();

        for (List<String> row : rows) {
            long populated = row.stream().filter(v -> !v.isBlank()).count();
            if (populated >= MIN_TABLE_COLUMNS) {
                pending.add(row);
                continue;
            }
            out.addAll(flush(pending, markdown));
            if (populated == 1) {
                out.add(row.stream().filter(v -> !v.isBlank()).findFirst().orElse(""));
            } else {
                out.add("");
            }
        }
        out.addAll(flush(pending, markdown));
        return out;
    }

    private List<String> flush(List<List<String>> block, boolean markdown) {
        if (block.isEmpty()) {
            return List.of();
        }
        int width = block.stream().mapToInt(List::size).max().orElse(0);
        List<String> out = new ArrayList<>();

        if (markdown) {
            out.add("");
            out.add(tableRow(block.get(0), width));
            out.add(separatorRow(width));
            for (int i = 1; i < block.size(); i++) {
                out.add(tableRow(block.get(i), width));
            }
            out.add("");
        } else {
            // Two spaces between values, matching what the flattened extraction paths emit, so a
            // downstream consumer sees the same token stream whichever target it asked for.
            for (List<String> row : block) {
                out.add(String.join("  ", padded(row, width)));
            }
        }
        block.clear();
        return out;
    }

    private String tableRow(List<String> row, int width) {
        List<String> cells = padded(row, width).stream().map(SpreadsheetService::escapePipes).toList();
        return "| " + String.join(" | ", cells) + " |";
    }

    private String separatorRow(int width) {
        return "| " + String.join(" | ", java.util.Collections.nCopies(width, "---")) + " |";
    }

    private List<String> padded(List<String> row, int width) {
        List<String> cells = new ArrayList<>(row);
        while (cells.size() < width) {
            cells.add("");
        }
        return cells;
    }

    /** A literal pipe in a cell would otherwise invent a column boundary. */
    private static String escapePipes(String value) {
        return value.replace("|", "\\|").replace("\n", " ").replace("\r", " ");
    }
}
