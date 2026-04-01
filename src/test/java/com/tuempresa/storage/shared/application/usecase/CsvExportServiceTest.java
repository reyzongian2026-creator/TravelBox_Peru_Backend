package com.tuempresa.storage.shared.application.usecase;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class CsvExportServiceTest {

    private final CsvExportService csvService = new CsvExportService();

    @Test
    void shouldExportSimpleCsv() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        List<String> headers = List.of("ID", "Name", "Email");
        List<TestItem> items = List.of(
            new TestItem(1, "John", "john@test.com"),
            new TestItem(2, "Jane", "jane@test.com")
        );
        List<Function<TestItem, String>> mappers = List.of(
            item -> String.valueOf(item.id),
            item -> item.name,
            item -> item.email
        );

        csvService.exportToCsv(out, headers, items, mappers);
        
        String csv = out.toString(StandardCharsets.UTF_8);
        assertTrue(csv.contains("ID"));
        assertTrue(csv.contains("Name"));
        assertTrue(csv.contains("Email"));
        assertTrue(csv.contains("John"));
        assertTrue(csv.contains("john@test.com"));
    }

    @Test
    void shouldIncludeBomForExcelCompatibility() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        List<String> headers = List.of("Name");
        List<TestItem> items = List.of(new TestItem(1, "Test", "test@test.com"));
        List<Function<TestItem, String>> mappers = List.of(item -> item.name);

        csvService.exportToCsv(out, headers, items, mappers);
        
        byte[] bytes = out.toByteArray();
        assertEquals(0xEF, bytes[0] & 0xFF);
        assertEquals(0xBB, bytes[1] & 0xFF);
        assertEquals(0xBF, bytes[2] & 0xFF);
    }

    @Test
    void shouldEscapeValuesWithCommas() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        List<String> headers = List.of("Name", "Description");
        List<TestItem> items = List.of(new TestItem(1, "Item, With Comma", "desc@test.com"));
        List<Function<TestItem, String>> mappers = List.of(
            item -> item.name,
            item -> item.email
        );

        csvService.exportToCsv(out, headers, items, mappers);
        
        String csv = out.toString(StandardCharsets.UTF_8);
        assertTrue(csv.contains("\"Item, With Comma\""));
    }

    @Test
    void shouldEscapeValuesWithQuotes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        List<String> headers = List.of("Name");
        List<TestItem> items = List.of(new TestItem(1, "Item \"Quoted\"", "test@test.com"));
        List<Function<TestItem, String>> mappers = List.of(item -> item.name);

        csvService.exportToCsv(out, headers, items, mappers);
        
        String csv = out.toString(StandardCharsets.UTF_8);
        assertTrue(csv.contains("\"Item \"\"Quoted\"\"\""));
    }

    @Test
    void shouldHandleNullValues() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        List<String> headers = List.of("Name", "Email");
        List<TestItem> items = List.of(new TestItem(1, null, null));
        List<Function<TestItem, String>> mappers = List.of(
            item -> item.name,
            item -> item.email
        );

        csvService.exportToCsv(out, headers, items, mappers);
        
        String csv = out.toString(StandardCharsets.UTF_8);
        assertNotNull(csv);
    }

    @Test
    void shouldExportWithHeader() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        List<String> headers = List.of("ID", "Name");
        List<TestItem> items = List.of(new TestItem(1, "Test", "test@test.com"));
        List<Function<TestItem, String>> mappers = List.of(
            item -> String.valueOf(item.id),
            item -> item.name
        );

        csvService.exportToCsvWithHeader(out, "Test Export", headers, items, mappers);
        
        String csv = out.toString(StandardCharsets.UTF_8);
        assertTrue(csv.contains("# Test Export"));
        assertTrue(csv.contains("# Generated:"));
    }

    @Test
    void shouldFormatInstantCorrectly() {
        Instant instant = Instant.parse("2024-06-15T10:30:00Z");
        String formatted = csvService.formatInstant(instant);
        
        assertNotNull(formatted);
        assertTrue(formatted.contains("2024"));
        assertTrue(formatted.contains("15"));
    }

    @Test
    void shouldFormatBooleanAsYesNo() {
        assertEquals("Si", csvService.formatBoolean(true));
        assertEquals("No", csvService.formatBoolean(false));
    }

    @Test
    void shouldReturnEmptyStringForNullInstant() {
        assertEquals("", csvService.formatInstant(null));
    }

    private static class TestItem {
        final int id;
        final String name;
        final String email;

        TestItem(int id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }
    }
}
