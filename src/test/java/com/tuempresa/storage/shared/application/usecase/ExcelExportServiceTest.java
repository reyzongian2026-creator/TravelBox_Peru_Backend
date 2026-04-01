package com.tuempresa.storage.shared.application.usecase;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcelExportServiceTest {

    private ExcelExportService excelExportService;

    @BeforeEach
    void setUp() {
        excelExportService = new ExcelExportService();
    }

    @Test
    void exportToExcel_validInput_returnsNonEmptyBytes() throws IOException {
        List<String> headers = List.of("Name", "Age");
        List<String[]> data = List.of(new String[]{"Alice", "30"}, new String[]{"Bob", "25"});
        List<java.util.function.Function<String[], String>> mappers = List.of(
                row -> row[0],
                row -> row[1]
        );

        byte[] result = excelExportService.exportToExcel("Sheet1", "Report Title", headers, data, mappers);

        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    void exportToExcel_validInput_producesValidWorkbook() throws IOException {
        List<String> headers = List.of("Col1", "Col2");
        List<String[]> data = Collections.singletonList(new String[]{"val1", "val2"});
        List<java.util.function.Function<String[], String>> mappers = List.of(row -> row[0], row -> row[1]);

        byte[] result = excelExportService.exportToExcel("TestSheet", "Title", headers, data, mappers);

        // Parse the bytes back into a workbook to confirm they are valid XLSX
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            assertNotNull(workbook.getSheet("TestSheet"));
        }
    }

    @Test
    void exportToExcel_emptyDataList_producesWorkbookWithOnlyHeaderRows() throws IOException {
        List<String> headers = List.of("Name", "Value");
        List<java.util.function.Function<Object, String>> mappers = List.of(o -> "", o -> "");

        byte[] result = excelExportService.exportToExcel("EmptySheet", "Empty Report", headers, List.of(), mappers);

        assertNotNull(result);
        assertTrue(result.length > 0);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var sheet = workbook.getSheet("EmptySheet");
            assertNotNull(sheet);
            // Row 0: title, Row 2: headers — row 1 is never created, so only 2 physical rows
            assertEquals(2, sheet.getPhysicalNumberOfRows());
        }
    }

    @Test
    void exportToExcel_nullMapperValue_writesEmptyString() throws IOException {
        List<String> headers = List.of("Name", "Extra");
        List<String[]> data = Collections.singletonList(new String[]{null, "x"});
        List<java.util.function.Function<String[], String>> mappers = List.of(row -> row[0], row -> row[1]);

        // Should not throw; null value from mapper is converted to ""
        byte[] result = excelExportService.exportToExcel("Sheet", "Title", headers, data, mappers);

        assertNotNull(result);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var sheet = workbook.getSheet("Sheet");
            // Row 3 (index 3) is the first data row; cell value should be ""
            assertEquals("", sheet.getRow(3).getCell(0).getStringCellValue());
        }
    }

    @Test
    void exportToExcel_sheetNameWithSpecialChars_sanitizesName() throws IOException {
        // Characters [ ] * / \ ? : are illegal in Excel sheet names
        List<String> headers = List.of("H1", "H2");
        List<java.util.function.Function<String, String>> mappers = List.of(s -> s, s -> s);

        byte[] result = excelExportService.exportToExcel("My/Sheet*Name", "T", headers, List.of(), mappers);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            // Sheet should be created with sanitized name (special chars replaced by _)
            assertNotNull(workbook.getSheet("My_Sheet_Name"));
        }
    }

    @Test
    void exportToExcel_blankSheetName_defaultsToReporte() throws IOException {
        List<String> headers = List.of("H1", "H2");
        List<java.util.function.Function<String, String>> mappers = List.of(s -> s, s -> s);

        byte[] result = excelExportService.exportToExcel("   ", "T", headers, List.of(), mappers);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            assertNotNull(workbook.getSheet("Reporte"));
        }
    }

    @Test
    void exportToExcel_nullSheetName_defaultsToReporte() throws IOException {
        List<String> headers = List.of("H1", "H2");
        List<java.util.function.Function<String, String>> mappers = List.of(s -> s, s -> s);

        byte[] result = excelExportService.exportToExcel(null, "T", headers, List.of(), mappers);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            assertNotNull(workbook.getSheet("Reporte"));
        }
    }

    @Test
    void exportToExcel_titleCellIsPopulated() throws IOException {
        List<String> headers = List.of("Col1", "Col2");
        List<java.util.function.Function<String, String>> mappers = List.of(s -> s, s -> s);

        byte[] result = excelExportService.exportToExcel("S", "My Title", headers, List.of(), mappers);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var sheet = workbook.getSheet("S");
            assertEquals("My Title", sheet.getRow(0).getCell(0).getStringCellValue());
        }
    }
}
