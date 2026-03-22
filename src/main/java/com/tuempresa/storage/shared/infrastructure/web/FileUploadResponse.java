package com.tuempresa.storage.shared.infrastructure.web;

public record FileUploadResponse(
        boolean success,
        String filename,
        String url,
        String contentType,
        long size,
        String error
) {
    public static FileUploadResponse success(String filename, String url, String contentType, long size) {
        return new FileUploadResponse(true, filename, url, contentType, size, null);
    }

    public static FileUploadResponse error(String error) {
        return new FileUploadResponse(false, null, null, null, 0, error);
    }
}