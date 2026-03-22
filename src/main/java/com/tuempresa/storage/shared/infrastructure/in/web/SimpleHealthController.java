package com.tuempresa.storage.shared.infrastructure.in.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/")
@ConditionalOnProperty(name = "app.health.simple.enabled", havingValue = "true", matchIfMissing = true)
public class SimpleHealthController {

    private static final Logger log = LoggerFactory.getLogger(SimpleHealthController.class);

    @Value("${spring.application.name:storage}")
    private String applicationName;

    @Value("${server.port:8080}")
    private String serverPort;

    @GetMapping(value = "health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> health() {
        log.info("Health check endpoint called");
        return Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString(),
                "port", serverPort,
                "service", applicationName
        );
    }

    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> root() {
        log.info("Root endpoint called");
        return Map.of(
                "service", applicationName,
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
    }
}
