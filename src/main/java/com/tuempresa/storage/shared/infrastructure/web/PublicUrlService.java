package com.tuempresa.storage.shared.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class PublicUrlService {

    private final String configuredBaseUrl;

    public PublicUrlService(@Value("${app.public-base-url:}") String configuredBaseUrl) {
        this.configuredBaseUrl = configuredBaseUrl != null ? configuredBaseUrl.trim() : "";
    }

    public String absolute(String path) {
        if (path == null || path.isBlank() || isAbsolute(path)) {
            return path;
        }
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        String baseUrl = resolveBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return normalizedPath;
        }
        return stripTrailingSlash(baseUrl) + normalizedPath;
    }

    private String resolveBaseUrl() {
        if (!configuredBaseUrl.isBlank()) {
            return configuredBaseUrl;
        }
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "";
        }
        HttpServletRequest request = attrs.getRequest();
        StringBuilder builder = new StringBuilder();
        builder.append(request.getScheme())
                .append("://")
                .append(request.getServerName());
        int port = request.getServerPort();
        if (!(port == 80 || port == 443)) {
            builder.append(':').append(port);
        }
        return builder.toString();
    }

    private String stripTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private boolean isAbsolute(String value) {
        return value.startsWith("http://") || value.startsWith("https://") || value.startsWith("data:");
    }
}
