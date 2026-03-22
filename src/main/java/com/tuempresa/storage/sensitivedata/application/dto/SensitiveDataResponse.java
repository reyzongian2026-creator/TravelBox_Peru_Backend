package com.tuempresa.storage.sensitivedata.application.dto;

public record SensitiveDataResponse(
        Long userId,
        String phone,
        String addressLine,
        String primaryDocumentNumber,
        String secondaryDocumentNumber,
        String emergencyContactName,
        String emergencyContactPhone
) {
}
