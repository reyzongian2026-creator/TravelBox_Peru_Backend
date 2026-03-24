package com.tuempresa.storage.shared.application.i18n;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Service
public class I18nReportService {

    private static final Logger log = LoggerFactory.getLogger(I18nReportService.class);
    
    private final ConcurrentMap<String, I18nErrorEntry> allErrors = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    
    public I18nReportService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    public void reportError(String locale, String key, String context, String reportedBy) {
        String errorId = UUID.randomUUID().toString();
        I18nErrorEntry entry = new I18nErrorEntry(
            errorId,
            key,
            locale,
            Instant.now(),
            context != null ? context : "unknown",
            reportedBy,
            "frontend"
        );
        
        String compositeKey = locale + ":" + key;
        allErrors.put(compositeKey, entry);
        
        log.warn("[I18N] Missing translation: key='{}' locale='{}' context='{}' reportedBy='{}'", 
            key, locale, context, reportedBy);
    }
    
    public void reportError(String locale, String key, String context) {
        reportError(locale, key, context, "anonymous");
    }
    
    public void reportBackendError(String source, String errorCode, String errorMessage, String stackTrace, String reportedBy) {
        String errorId = UUID.randomUUID().toString();
        I18nErrorEntry entry = new I18nErrorEntry(
            errorId,
            errorCode,
            "backend",
            Instant.now(),
            source,
            reportedBy,
            "backend"
        );
        entry.setBackendDetails(errorCode, errorMessage, stackTrace);
        
        allErrors.put(errorId, entry);
        
        log.error("[BACKEND ERROR] source='{}' code='{}' message='{}' reportedBy='{}'", 
            source, errorCode, errorMessage, reportedBy);
    }
    
    public void reportErrors(String locale, List<String> keys, String context, String reportedBy) {
        for (String key : keys) {
            reportError(locale, key, context, reportedBy);
        }
    }
    
    public List<I18nErrorEntry> getAllErrors() {
        return new ArrayList<>(allErrors.values());
    }
    
    public List<I18nErrorEntry> getErrorsByLocale(String locale) {
        return allErrors.values().stream()
            .filter(e -> locale.equals(e.locale()))
            .collect(Collectors.toList());
    }
    
    public List<I18nErrorEntry> getErrorsByType(String type) {
        return allErrors.values().stream()
            .filter(e -> type.equals(e.errorType()))
            .collect(Collectors.toList());
    }
    
    public List<I18nErrorEntry> getErrorsByUser(String reportedBy) {
        return allErrors.values().stream()
            .filter(e -> reportedBy.equals(e.reportedBy()))
            .collect(Collectors.toList());
    }
    
    public I18nReportSummary getSummary() {
        Map<String, Long> byLocale = allErrors.values().stream()
            .collect(Collectors.groupingBy(I18nErrorEntry::locale, Collectors.counting()));
        
        Map<String, Long> byType = allErrors.values().stream()
            .collect(Collectors.groupingBy(I18nErrorEntry::errorType, Collectors.counting()));
        
        Map<String, Long> byUser = allErrors.values().stream()
            .collect(Collectors.groupingBy(I18nErrorEntry::reportedBy, Collectors.counting()));
        
        return new I18nReportSummary(
            allErrors.size(),
            byLocale,
            byType,
            byUser,
            Instant.now()
        );
    }
    
    public void clearAllErrors() {
        allErrors.clear();
        log.info("Cleared all i18n error reports");
    }
    
    public void clearErrorsByLocale(String locale) {
        allErrors.entrySet().removeIf(e -> locale.equals(e.getValue().locale()));
        log.info("Cleared i18n error reports for locale: {}", locale);
    }
    
    public void clearErrorsByUser(String reportedBy) {
        allErrors.entrySet().removeIf(e -> reportedBy.equals(e.getValue().reportedBy()));
        log.info("Cleared i18n error reports for user: {}", reportedBy);
    }
    
    public String exportToJson() {
        try {
            I18nExportReport report = new I18nExportReport(
                "TravelBox Peru",
                "1.0",
                Instant.now(),
                getSummary(),
                getAllErrors()
            );
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            log.error("Failed to export i18n report: {}", e.getMessage(), e);
            return "{\"error\": \"Failed to export report: " + e.getMessage() + "\"}";
        }
    }
    
    public String getReportAsJson() {
        return exportToJson();
    }
    
    public static class I18nErrorEntry {
        private final String id;
        private final String key;
        private final String locale;
        private final Instant timestamp;
        private final String context;
        private final String reportedBy;
        private final String errorType;
        private String backendErrorCode;
        private String backendErrorMessage;
        private String backendStackTrace;
        
        public I18nErrorEntry(String id, String key, String locale, Instant timestamp, 
                              String context, String reportedBy, String errorType) {
            this.id = id;
            this.key = key;
            this.locale = locale;
            this.timestamp = timestamp;
            this.context = context;
            this.reportedBy = reportedBy;
            this.errorType = errorType;
        }
        
        public void setBackendDetails(String errorCode, String errorMessage, String stackTrace) {
            this.backendErrorCode = errorCode;
            this.backendErrorMessage = errorMessage;
            this.backendStackTrace = stackTrace;
        }
        
        public String id() { return id; }
        public String key() { return key; }
        public String locale() { return locale; }
        public Instant timestamp() { return timestamp; }
        public String context() { return context; }
        public String reportedBy() { return reportedBy; }
        public String errorType() { return errorType; }
        public Optional<String> backendErrorCode() { return Optional.ofNullable(backendErrorCode); }
        public Optional<String> backendErrorMessage() { return Optional.ofNullable(backendErrorMessage); }
        public Optional<String> backendStackTrace() { return Optional.ofNullable(backendStackTrace); }
    }
    
    public record I18nReportSummary(
        int totalErrors,
        Map<String, Long> errorsByLocale,
        Map<String, Long> errorsByType,
        Map<String, Long> errorsByUser,
        Instant generatedAt
    ) {}
    
    public record I18nExportReport(
        String application,
        String version,
        Instant exportedAt,
        I18nReportSummary summary,
        List<I18nErrorEntry> errors
    ) {}
}
