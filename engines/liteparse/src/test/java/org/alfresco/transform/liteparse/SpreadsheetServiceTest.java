package org.alfresco.transform.liteparse;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the direct spreadsheet path. No container required: the class is a pure function from
 * a workbook to text.
 *
 * <p>The behaviour these pin is why the path exists. Routing a spreadsheet through LiteParse sends it
 * via a LibreOffice page render, which silently drops any column that does not fit the rendered page
 * (measured: two of five columns survived a rate card) and cannot produce a table, because LiteParse
 * 2.0.4 exposes no table model. Reading cells keeps every column and the grid is already a table.</p>
 */
class SpreadsheetServiceTest {

    private final SpreadsheetService service = new SpreadsheetService();

    @TempDir
    Path tempDir;

    @Test
    void everyColumnSurvivesAndBecomesAMarkdownTable() throws Exception {
        File xlsx = workbook("Rates", new String[][]{
                {"Role", "Standard", "Out of hours", "Weekend", "Public holiday"},
                {"Delivery director", "2,400", "3,600", "4,800", "6,000"},
                {"Principal architect", "1,950", "2,925", "3,900", "4,875"},
        });

        String md = convert(xlsx, "text/markdown");

        assertThat(md).contains("| Role | Standard | Out of hours | Weekend | Public holiday |");
        assertThat(md).contains("| --- | --- | --- | --- | --- |");
        assertThat(md).contains("| Delivery director | 2,400 | 3,600 | 4,800 | 6,000 |");
        assertThat(md).contains("| Principal architect | 1,950 | 2,925 | 3,900 | 4,875 |");
        // The sheet name is a heading, which gives a consumer a section boundary.
        assertThat(md).contains("## Rates");
    }

    @Test
    void plainTextTargetSeparatesCellsWithTwoSpacesAndNoPipes() throws Exception {
        File xlsx = workbook("Rates", new String[][]{
                {"Role", "Standard"},
                {"Analyst", "780"},
        });

        String text = convert(xlsx, "text/plain");

        assertThat(text).doesNotContain("|");
        assertThat(text).contains("Role  Standard");
        assertThat(text).contains("Analyst  780");
    }

    /**
     * Real spreadsheets put narrative text in column A around the data. A single populated cell is a
     * paragraph, not a one-column table row, or the prose would be swallowed into the table above it.
     */
    @Test
    void interleavedProseStaysProseAndSeparatesTables() throws Exception {
        File xlsx = workbook("Doc", new String[][]{
                {"PROFESSIONAL SERVICES RATE CARD"},
                {},
                {"Rates are per person per working day."},
                {},
                {"Role", "Standard"},
                {"Analyst", "780"},
                {},
                {"Volume discounts apply as follows."},
                {},
                {"Block size", "Discount"},
                {"5", "0%"},
        });

        String md = convert(xlsx, "text/markdown");

        assertThat(md).contains("PROFESSIONAL SERVICES RATE CARD");
        assertThat(md).contains("Rates are per person per working day.");
        assertThat(md).contains("Volume discounts apply as follows.");
        // Prose lines must not have become table rows.
        assertThat(md).doesNotContain("| Rates are per person per working day. |");
        // Two separate tables, so two separator rows.
        assertThat(md.lines().filter(l -> l.startsWith("| --- |")).count()).isEqualTo(2);
        assertThat(md).contains("| Role | Standard |").contains("| Block size | Discount |");
    }

    @Test
    void aLiteralPipeInACellIsEscapedRatherThanInventingAColumn() throws Exception {
        File xlsx = workbook("Doc", new String[][]{
                {"Field", "Pattern"},
                {"separator", "a|b"},
        });

        String md = convert(xlsx, "text/markdown");

        assertThat(md).contains("| separator | a\\|b |");
        // The escaped pipe must not add a column: a two-column row has three *unescaped* delimiters,
        // and the escaped one is still a '|' character, so count only the undelimiting ones.
        String row = md.lines().filter(l -> l.startsWith("| separator")).findFirst().orElseThrow();
        assertThat(row.replaceAll("\\\\\\|", "").chars().filter(c -> c == '|').count()).isEqualTo(3);
    }

    @Test
    void raggedRowsArePaddedToTheWidestRow() throws Exception {
        File xlsx = workbook("Doc", new String[][]{
                {"A", "B", "C"},
                {"1", "2"},
        });

        String md = convert(xlsx, "text/markdown");

        assertThat(md).contains("| A | B | C |");
        assertThat(md).contains("| 1 | 2 |  |");
    }

    @Test
    void everySheetIsRendered() throws Exception {
        File xlsx = tempDir.resolve("multi.xlsx").toFile();
        try (XSSFWorkbook wb = new XSSFWorkbook(); FileOutputStream out = new FileOutputStream(xlsx)) {
            fill(wb.createSheet("First"), new String[][]{{"a", "b"}, {"1", "2"}});
            fill(wb.createSheet("Second"), new String[][]{{"c", "d"}, {"3", "4"}});
            wb.write(out);
        }

        String md = convert(xlsx, "text/markdown");

        assertThat(md).contains("## First").contains("## Second");
        assertThat(md).contains("| 1 | 2 |").contains("| 3 | 4 |");
    }

    @Test
    void anEmptySheetProducesNoTable() throws Exception {
        File xlsx = workbook("Empty", new String[][]{});

        String md = convert(xlsx, "text/markdown");

        assertThat(md).doesNotContain("| --- |");
    }

    // ──────────────────────────────────────────────────────────────────────

    private String convert(File xlsx, String targetMimetype) throws IOException {
        File out = service.convert(xlsx, targetMimetype);
        assertThat(out).exists();
        return Files.readString(out.toPath(), StandardCharsets.UTF_8);
    }

    private File workbook(String sheetName, String[][] rows) throws IOException {
        File xlsx = tempDir.resolve(sheetName + ".xlsx").toFile();
        try (XSSFWorkbook wb = new XSSFWorkbook(); FileOutputStream out = new FileOutputStream(xlsx)) {
            fill(wb.createSheet(sheetName), rows);
            wb.write(out);
        }
        return xlsx;
    }

    private static void fill(Sheet sheet, String[][] rows) {
        for (int r = 0; r < rows.length; r++) {
            Row row = sheet.createRow(r);
            for (int c = 0; c < rows[r].length; c++) {
                row.createCell(c).setCellValue(rows[r][c]);
            }
        }
    }
}
