package com.tuempresa.storage.shared.infrastructure.web;

import com.tuempresa.storage.shared.domain.exception.ApiException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ReactiveGlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleApiException(ApiException ex, ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.status(ex.getStatus())
                .body(buildError(ex.getStatus(), ex.getCode(), ex.getMessage(), exchange, List.of())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleValidationException(
            MethodArgumentNotValidException ex,
            ServerWebExchange exchange
    ) {
        List<String> details = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .toList();
        return Mono.just(ResponseEntity.badRequest()
                .body(buildError(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "El payload tiene errores de validacion.",
                        exchange,
                        details
                )));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleWebExchangeBindException(
            WebExchangeBindException ex,
            ServerWebExchange exchange
    ) {
        List<String> details = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .toList();
        return Mono.just(ResponseEntity.badRequest()
                .body(buildError(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "El payload tiene errores de validacion.",
                        exchange,
                        details
                )));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleConstraintViolation(
            ConstraintViolationException ex,
            ServerWebExchange exchange
    ) {
        List<String> details = ex.getConstraintViolations()
                .stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        return Mono.just(ResponseEntity.badRequest()
                .body(buildError(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "La solicitud no cumple las restricciones requeridas.",
                        exchange,
                        details
                )));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleAccessDenied(
            AccessDeniedException ex,
            ServerWebExchange exchange
    ) {
        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(buildError(
                        HttpStatus.FORBIDDEN,
                        "FORBIDDEN",
                        "No tienes permisos para esta operacion.",
                        exchange,
                        List.of()
                )));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Data integrity violation at {}: {}", exchange.getRequest().getPath(), ex.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildError(
                        HttpStatus.CONFLICT,
                        "DATA_INTEGRITY_ERROR",
                        "La operacion no se pudo completar por una restriccion de datos.",
                        exchange,
                        List.of()
                )));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            ServerWebExchange exchange
    ) {
        String parameter = ex.getName() != null ? ex.getName() : "parametro";
        return Mono.just(ResponseEntity.badRequest()
                .body(buildError(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_PARAMETER_TYPE",
                        "Valor invalido para '" + parameter + "'.",
                        exchange,
                        List.of()
                )));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleNoResourceFound(
            NoResourceFoundException ex,
            ServerWebExchange exchange
    ) {
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError(
                        HttpStatus.NOT_FOUND,
                        "RESOURCE_NOT_FOUND",
                        "El recurso solicitado no existe.",
                        exchange,
                        List.of()
                )));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleGenericException(
            Exception ex,
            ServerWebExchange exchange
    ) {
        if (exchange.getResponse().isCommitted() || isClientAbort(ex)) {
            log.debug("Client connection aborted at {}: {}", exchange.getRequest().getPath(), ex.getMessage());
            return Mono.empty();
        }
        log.error("Unexpected server error at {}", exchange.getRequest().getPath(), ex);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "UNEXPECTED_ERROR",
                        "Ocurrio un error inesperado en el servidor.",
                        exchange,
                        List.of()
                )));
    }

    private ApiErrorResponse buildError(
            HttpStatus status,
            String code,
            String message,
            ServerWebExchange exchange,
            List<String> details
    ) {
        return new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                exchange.getRequest().getPath().value(),
                details
        );
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private boolean isClientAbort(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof IOException ioEx && containsAbortSignature(ioEx.getMessage())) {
                return true;
            }
            String className = cursor.getClass().getName();
            if (className != null && className.contains("ClientAbortException")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private boolean containsAbortSignature(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("connection reset")
                || normalized.contains("broken pipe")
                || normalized.contains("forcibly closed")
                || normalized.contains("anulado una conex");
    }
}
