package com.tuempresa.storage.incidents.application.usecase;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.tuempresa.storage.incidents.application.dto.IncidentSummaryResponse;
import com.tuempresa.storage.incidents.domain.IncidentStatus;
import com.tuempresa.storage.reservations.domain.ReservationStatus;
import com.tuempresa.storage.shared.application.usecase.ExcelExportService;
import com.tuempresa.storage.shared.infrastructure.storage.AzureBlobStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

@Service
public class IncidentReportService {

    private static final Logger LOG = LoggerFactory.getLogger(IncidentReportService.class);

    private static final java.awt.Color PRIMARY_BLUE = new java.awt.Color(0x3C, 0x50, 0xE0);
    private static final java.awt.Color PRIMARY_TEAL = new java.awt.Color(0x46, 0x5F, 0xFF);
    private static final java.awt.Color SEAFOAM = new java.awt.Color(0x80, 0xCA, 0xEE);
    private static final java.awt.Color SAND = new java.awt.Color(0xF0, 0x95, 0x0C);
    private static final java.awt.Color TEXT_BODY = new java.awt.Color(0x64, 0x74, 0x8B);
    private static final java.awt.Color LIGHT_BG = new java.awt.Color(0xF7, 0xF9, 0xFC);
    private static final java.awt.Color BORDER_COLOR = new java.awt.Color(0xE5, 0xE8, 0xEC);
    private static final java.awt.Color WHITE = java.awt.Color.WHITE;
    private static final java.awt.Color OPEN_GREEN = new java.awt.Color(0x10, 0xB9, 0x81);
    private static final java.awt.Color RESOLVED_BLUE = new java.awt.Color(0x3C, 0x50, 0xE0);
    private static final java.awt.Color CANCELLED_RED = new java.awt.Color(0xEF, 0x44, 0x44);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("America/Lima"));

    private final AzureBlobStorageService blobStorageService;
    private final ExcelExportService excelExportService;

    public IncidentReportService(
            AzureBlobStorageService blobStorageService,
            ExcelExportService excelExportService
    ) {
        this.blobStorageService = blobStorageService;
        this.excelExportService = excelExportService;
    }

    public record ReportResult(String fileName, String downloadUrl, String format) {}

    public ReportResult generatePdfReport(List<IncidentSummaryResponse> incidents, String generatedBy) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 40, 40, 60, 60);
            PdfWriter writer = PdfWriter.getInstance(document, baos);

            document.open();

            addHeader(writer, document);
            addTitle("Incident Report - TravelBox Peru", document);
            addMetadataTable(incidents, generatedBy, document);
            addIncidentTable(incidents, document);
            addFooter(document);

            document.close();

            byte[] pdfBytes = baos.toByteArray();
            String timestamp = java.time.LocalDateTime.now().toString().replace(":", "-").replace(".", "-");
            String fileName = "incident_report_" + timestamp + ".pdf";

            String downloadUrl = blobStorageService.uploadReport(pdfBytes, fileName, "application/pdf", true);
            LOG.info("PDF report uploaded: {}", fileName);

            return new ReportResult(fileName, downloadUrl, "PDF");
        } catch (Exception e) {
            LOG.error("Error generating PDF report", e);
            throw new RuntimeException("Failed to generate PDF report: " + e.getMessage(), e);
        }
    }

    public ReportResult generateExcelReport(List<IncidentSummaryResponse> incidents, String generatedBy) {
        try {
            List<String> headers = List.of(
                    "ID",
                    "Reserva",
                    "Almacen",
                    "Direccion almacen",
                    "Cliente",
                    "Correo cliente",
                    "Telefono cliente",
                    "Estado reserva",
                    "Estado incidencia",
                    "Creado",
                    "Resuelto",
                    "Descripcion",
                    "Resolucion",
                    "Generado por"
            );
            List<Function<IncidentSummaryResponse, String>> mappers = List.of(
                    incident -> String.valueOf(incident.id()),
                    incident -> safe(incident.reservationCode()),
                    incident -> safe(incident.warehouseName()),
                    incident -> safe(incident.warehouseAddress()),
                    incident -> safe(incident.customerName()),
                    incident -> safe(incident.customerEmail()),
                    incident -> safe(incident.customerPhone()),
                    incident -> formatReservationStatus(incident.reservationStatus()),
                    incident -> incident.status() != null ? incident.status().name() : "-",
                    incident -> formatInstant(incident.createdAt()),
                    incident -> formatInstant(incident.resolvedAt()),
                    incident -> safe(incident.description()),
                    incident -> safe(incident.resolution()),
                    incident -> generatedBy
            );

            byte[] excelBytes = excelExportService.exportToExcel(
                    "Incidencias",
                    "Reporte de incidencias - InkaVoy",
                    headers,
                    incidents,
                    mappers
            );

            String timestamp = java.time.LocalDateTime.now().toString().replace(":", "-").replace(".", "-");
            String fileName = "incident_report_" + timestamp + ".xlsx";

            String downloadUrl = blobStorageService.uploadReport(
                    excelBytes,
                    fileName,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    true
            );
            LOG.info("Excel report uploaded: {}", fileName);

            return new ReportResult(fileName, downloadUrl, "XLSX");
        } catch (Exception e) {
            LOG.error("Error generating Excel report", e);
            throw new RuntimeException("Failed to generate Excel report: " + e.getMessage(), e);
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private void addHeader(PdfWriter writer, Document document) throws DocumentException {
        PdfContentByte canvas = writer.getDirectContentUnder();

        Rectangle headerBg = new Rectangle(0, document.top(), document.right(), document.top() + 80);
        canvas.setColorFill(PRIMARY_BLUE);
        canvas.rectangle(headerBg.getLeft(), headerBg.getBottom(), headerBg.getWidth(), headerBg.getHeight());
        canvas.fill();

        canvas.setColorFill(PRIMARY_TEAL);
        canvas.rectangle(headerBg.getLeft(), headerBg.getBottom(), headerBg.getWidth(), 10);
        canvas.fill();

        ColumnText.showTextAligned(
                canvas, Element.ALIGN_LEFT,
                new Phrase("TravelBox Peru", new Font(Font.HELVETICA, 22, Font.BOLD, WHITE)),
                document.left() + 60, document.top() + 35, 0
        );

        ColumnText.showTextAligned(
                canvas, Element.ALIGN_LEFT,
                new Phrase("Incident Management System", new Font(Font.HELVETICA, 10, Font.NORMAL, SEAFOAM)),
                document.left() + 60, document.top() + 18, 0
        );

        ColumnText.showTextAligned(
                canvas, Element.ALIGN_RIGHT,
                new Phrase("inkavoy.pe", new Font(Font.HELVETICA, 10, Font.NORMAL, WHITE)),
                document.right() - 40, document.top() + 35, 0
        );

        canvas.setColorFill(SAND);
        canvas.rectangle(document.right() - 8, document.top() + 10, 4, 60);
        canvas.fill();
    }

    private void addTitle(String title, Document document) throws DocumentException {
        document.add(new Paragraph("\n"));
        Paragraph p = new Paragraph(title, new Font(Font.HELVETICA, 16, Font.BOLD, PRIMARY_BLUE));
        p.setAlignment(Element.ALIGN_CENTER);
        document.add(p);
        document.add(new Paragraph("\n"));
    }

    private void addMetadataTable(List<IncidentSummaryResponse> incidents, String generatedBy, Document document) throws DocumentException {
        PdfPTable metaTable = new PdfPTable(4);
        metaTable.setWidthPercentage(100);
        metaTable.setSpacingBefore(5);
        metaTable.setSpacingAfter(15);

        long openCount = incidents.stream().filter(i -> i.status() == IncidentStatus.OPEN).count();
        long resolvedCount = incidents.stream().filter(i -> i.status() == IncidentStatus.RESOLVED).count();

        addMetaCell(metaTable, "Total Incidents", String.valueOf(incidents.size()), PRIMARY_BLUE);
        addMetaCell(metaTable, "Open", String.valueOf(openCount), SAND);
        addMetaCell(metaTable, "Resolved", String.valueOf(resolvedCount), OPEN_GREEN);
        addMetaCell(metaTable, "Generated By", generatedBy, TEXT_BODY);

        document.add(metaTable);

        Paragraph genInfo = new Paragraph(
                "Generated: " + DATE_FORMATTER.format(Instant.now()) + " | Period: All Time",
                new Font(Font.HELVETICA, 8, Font.ITALIC, TEXT_BODY)
        );
        genInfo.setAlignment(Element.ALIGN_CENTER);
        document.add(genInfo);
        document.add(new Paragraph("\n"));
    }

    private void addMetaCell(PdfPTable table, String label, String value, java.awt.Color valueColor) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(LIGHT_BG);
        cell.setPadding(8);

        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "\n", new Font(Font.HELVETICA, 7, Font.NORMAL, TEXT_BODY)));
        p.add(new Chunk(value, new Font(Font.HELVETICA, 14, Font.BOLD, valueColor)));
        p.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(p);
        table.addCell(cell);
    }

    private void addIncidentTable(List<IncidentSummaryResponse> incidents, Document document) throws DocumentException {
        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 2.5f, 3, 2.5f, 3, 2.5f, 2});
        table.setSpacingBefore(10);

        java.awt.Color[] headerColors = {PRIMARY_BLUE, PRIMARY_TEAL, PRIMARY_BLUE, PRIMARY_TEAL, PRIMARY_BLUE, PRIMARY_TEAL, PRIMARY_TEAL};

        String[] headers = {"ID", "Reservation", "Warehouse", "Client", "Status", "Created", "Resolved"};
        for (int i = 0; i < headers.length; i++) {
            PdfPCell cell = new PdfPCell(new Phrase(headers[i], new Font(Font.HELVETICA, 9, Font.BOLD, WHITE)));
            cell.setBackgroundColor(headerColors[i]);
            cell.setPadding(8);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        table.setHeaderRows(1);

        boolean alternate = false;
        for (IncidentSummaryResponse incident : incidents) {
            java.awt.Color rowColor = alternate ? LIGHT_BG : WHITE;
            alternate = !alternate;

            addTableCell(table, String.valueOf(incident.id()), rowColor, Element.ALIGN_CENTER);
            addTableCell(table, incident.reservationCode(), rowColor, Element.ALIGN_CENTER);
            addTableCell(table, incident.warehouseName(), rowColor, Element.ALIGN_LEFT);
            addTableCell(table, incident.customerName(), rowColor, Element.ALIGN_LEFT);
            addStatusCell(table, incident.status(), rowColor);
            addTableCell(table, formatInstant(incident.createdAt()), rowColor, Element.ALIGN_CENTER);
            addTableCell(table, formatInstant(incident.resolvedAt()), rowColor, Element.ALIGN_CENTER);
        }

        document.add(table);
    }

    private void addTableCell(PdfPTable table, String text, java.awt.Color bgColor, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", new Font(Font.HELVETICA, 8, Font.NORMAL, TEXT_BODY)));
        cell.setBackgroundColor(bgColor);
        cell.setPadding(6);
        cell.setHorizontalAlignment(alignment);
        cell.setBorderColor(BORDER_COLOR);
        table.addCell(cell);
    }

    private void addStatusCell(PdfPTable table, IncidentStatus status, java.awt.Color bgColor) {
        java.awt.Color statusColor = status == IncidentStatus.OPEN ? OPEN_GREEN :
                status == IncidentStatus.RESOLVED ? RESOLVED_BLUE : CANCELLED_RED;
        String statusText = status == IncidentStatus.OPEN ? "OPEN" :
                status == IncidentStatus.RESOLVED ? "RESOLVED" : "CANCELLED";

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bgColor);
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBorderColor(BORDER_COLOR);

        Paragraph p = new Paragraph(statusText, new Font(Font.HELVETICA, 8, Font.BOLD, statusColor));
        p.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(p);
        table.addCell(cell);
    }

    private void addFooter(Document document) throws DocumentException {
        document.add(new Paragraph("\n\n"));

        PdfPTable footerTable = new PdfPTable(3);
        footerTable.setWidthPercentage(100);

        PdfPCell leftCell = new PdfPCell(new Phrase("TravelBox Peru © " + java.time.Year.now().getValue(),
                new Font(Font.HELVETICA, 7, Font.NORMAL, TEXT_BODY)));
        leftCell.setBorder(Rectangle.TOP);
        leftCell.setBorderColor(BORDER_COLOR);
        leftCell.setPaddingTop(5);

        PdfPCell centerCell = new PdfPCell(new Phrase("Confidential - For internal use only",
                new Font(Font.HELVETICA, 7, Font.ITALIC, TEXT_BODY)));
        centerCell.setBorder(Rectangle.TOP);
        centerCell.setBorderColor(BORDER_COLOR);
        centerCell.setPaddingTop(5);
        centerCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        PdfPCell rightCell = new PdfPCell(new Phrase("Page 1",
                new Font(Font.HELVETICA, 7, Font.NORMAL, TEXT_BODY)));
        rightCell.setBorder(Rectangle.TOP);
        rightCell.setBorderColor(BORDER_COLOR);
        rightCell.setPaddingTop(5);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        footerTable.addCell(leftCell);
        footerTable.addCell(centerCell);
        footerTable.addCell(rightCell);

        document.add(footerTable);
    }

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "-";
        }
        return DATE_FORMATTER.format(instant);
    }

    private String formatReservationStatus(ReservationStatus status) {
        if (status == null) {
            return "-";
        }
        return status.name().replace("_", " ");
    }
}
