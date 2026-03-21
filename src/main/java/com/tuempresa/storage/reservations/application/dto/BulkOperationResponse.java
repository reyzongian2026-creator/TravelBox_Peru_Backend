package com.tuempresa.storage.reservations.application.dto;

public record BulkOperationResponse(
        int processed,
        int succeeded,
        int failed,
        String message
) {
    public static BulkOperationResponse of(int processed, int succeeded, int failed, String message) {
        return new BulkOperationResponse(processed, succeeded, failed, message);
    }

    public static BulkOperationResponse success(int total, String action) {
        return new BulkOperationResponse(total, total, 0, action + " completado exitosamente.");
    }

    public static BulkOperationResponse partial(int total, int succeeded, int failed, String action) {
        return new BulkOperationResponse(total, succeeded, failed, 
                action + ": " + succeeded + " exitosos, " + failed + " fallidos.");
    }
}
