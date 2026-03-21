package com.tuempresa.storage.shared.application.usecase;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

@Component
public class CsvExportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public <T> void exportToCsv(
            OutputStream outputStream,
            List<String> headers,
            List<T> data,
            List<Function<T, String>> columnMappers
    ) throws IOException {
        try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            writeBom(writer);
            writeLine(writer, headers);
            for (T item : data) {
                List<String> row = columnMappers.stream()
                        .map(mapper -> mapper.apply(item))
                        .toList();
                writeLine(writer, row);
            }
            writer.flush();
        }
    }

    public <T> void exportToCsvWithHeader(
            OutputStream outputStream,
            String title,
            List<String> headers,
            List<T> data,
            List<Function<T, String>> columnMappers
    ) throws IOException {
        try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            writeBom(writer);
            if (title != null && !title.isBlank()) {
                writer.write("# ");
                writer.write(title);
                writer.write("\n");
            }
            writer.write("# Generated: ");
            writer.write(DATE_FORMATTER.format(Instant.now()));
            writer.write("\n\n");
            writeLine(writer, headers);
            for (T item : data) {
                List<String> row = columnMappers.stream()
                        .map(mapper -> mapper.apply(item))
                        .toList();
                writeLine(writer, row);
            }
            writer.flush();
        }
    }

    private void writeBom(Writer writer) throws IOException {
        writer.write("\uFEFF");
    }

    private void writeLine(Writer writer, List<String> columns) throws IOException {
        for (int i = 0; i < columns.size(); i++) {
            String value = columns.get(i);
            if (value == null) {
                value = "";
            }
            if (needsQuoting(value)) {
                writer.write("\"");
                writer.write(escapeQuotes(value));
                writer.write("\"");
            } else {
                writer.write(value);
            }
            if (i < columns.size() - 1) {
                writer.write(",");
            }
        }
        writer.write("\n");
    }

    private boolean needsQuoting(String value) {
        return value.contains(",") ||
               value.contains("\"") ||
               value.contains("\n") ||
               value.contains("\r");
    }

    private String escapeQuotes(String value) {
        return value.replace("\"", "\"\"");
    }

    public String formatInstant(Instant instant) {
        if (instant == null) {
            return "";
        }
        return DATE_FORMATTER.format(instant);
    }

    public String formatBoolean(Boolean value) {
        if (value == null) {
            return "";
        }
        return value ? "Si" : "No";
    }
}
