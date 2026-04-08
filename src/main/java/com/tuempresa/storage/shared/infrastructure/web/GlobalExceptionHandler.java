package com.tuempresa.storage.shared.infrastructure.web;

import com.tuempresa.storage.shared.domain.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GlobalExceptionHandler {

        private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

        @ExceptionHandler(ApiException.class)
        public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
                log.warn("API error [{}] at {}: {}", ex.getCode(), request.getRequestURI(), ex.getMessage());
                return ResponseEntity.status(ex.getStatus())
                                .body(new ApiErrorResponse(
                                                Instant.now(),
                                                ex.getStatus().value(),
                                                ex.getStatus().getReasonPhrase(),
                                                ex.getCode(),
                                                ex.getMessage(),
                                                request.getRequestURI(),
                                                List.of()));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiErrorResponse> handleValidationException(
                        MethodArgumentNotValidException ex,
                        HttpServletRequest request) {
                log.warn("Validation error at {}: {}", request.getRequestURI(), ex.getMessage());
                List<String> details = ex.getBindingResult().getFieldErrors()
                                .stream()
                                .map(this::formatFieldError)
                                .toList();
                return ResponseEntity.badRequest()
                                .body(new ApiErrorResponse(
                                                Instant.now(),
                                                HttpStatus.BAD_REQUEST.value(),
                                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                                "VALIDATION_ERROR",
                                                "El payload tiene errores de validacion.",
                                                request.getRequestURI(),
                                                details));
        }

        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
                        ConstraintViolationException ex,
                        HttpServletRequest request) {
                log.warn("Constraint violation at {}: {}", request.getRequestURI(), ex.getMessage());
                List<String> details = ex.getConstraintViolations()
                                .stream()
                                .map(v -> v.getMessage())
                                .toList();
                return ResponseEntity.badRequest()
                                .body(new ApiErrorResponse(
                                                Instant.now(),
                                                HttpStatus.BAD_REQUEST.value(),
                                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                                "VALIDATION_ERROR",
                                                "La solicitud no cumple las restricciones requeridas.",
                                                request.getRequestURI(),
                                                details));
        }

        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleNoResourceFound(
                        NoResourceFoundException ex,
                        HttpServletRequest request) {
                log.warn("Resource not found at {}: {}", request.getRequestURI(), ex.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(new ApiErrorResponse(
                                                Instant.now(),
                                                HttpStatus.NOT_FOUND.value(),
                                                HttpStatus.NOT_FOUND.getReasonPhrase(),
                                                "NOT_FOUND",
                                                "El recurso solicitado no existe.",
                                                request.getRequestURI(),
                                                List.of()));
        }

        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
                        HttpRequestMethodNotSupportedException ex,
                        HttpServletRequest request) {
                log.warn("Method not allowed at {}: {}", request.getRequestURI(), ex.getMessage());
                return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                                .body(new ApiErrorResponse(
                                                Instant.now(),
                                                HttpStatus.METHOD_NOT_ALLOWED.value(),
                                                HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase(),
                                                "METHOD_NOT_ALLOWED",
                                                "El metodo HTTP no esta permitido para esta ruta.",
                                                request.getRequestURI(),
                                                List.of()));
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ApiErrorResponse> handleAccessDenied(
                        AccessDeniedException ex,
                        HttpServletRequest request) {
                log.warn("Access denied at {}: {}", request.getRequestURI(), ex.getMessage());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(new ApiErrorResponse(
                                                Instant.now(),
                                                HttpStatus.FORBIDDEN.value(),
                                                HttpStatus.FORBIDDEN.getReasonPhrase(),
                                                "FORBIDDEN",
                                                "No tienes permisos para esta operacion.",
                                                request.getRequestURI(),
                                                List.of()));
        }

        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
                        DataIntegrityViolationException ex,
                        HttpServletRequest request) {
                log.warn("Data integrity violation at {}: {}", request.getRequestURI(), ex.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(new ApiErrorResponse(
                                                Instant.now(),
                                                HttpStatus.CONFLICT.value(),
                                                HttpStatus.CONFLICT.getReasonPhrase(),
                                                "DATA_INTEGRITY_ERROR",
                                                "La operacion no se pudo completar por una restriccion de datos.",
                                                request.getRequestURI(),
                                                List.of()));
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatch(
                        MethodArgumentTypeMismatchException ex,
                        HttpServletRequest request) {
                log.warn("Type mismatch at {}: {}", request.getRequestURI(), ex.getMessage());
                String parameter = ex.getName() != null ? ex.getName() : "parametro";
                return ResponseEntity.badRequest()
                                .body(new ApiErrorResponse(
                                                Instant.now(),
                                                HttpStatus.BAD_REQUEST.value(),
                                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                                "INVALID_PARAMETER_TYPE",
                                                "Valor invalido para '" + parameter + "'.",
                                                request.getRequestURI(),
                                                List.of()));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
                log.error("Unexpected server error at {}", request.getRequestURI(), ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(new ApiErrorResponse(
                                                Instant.now(),
                                                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                                                "UNEXPECTED_ERROR",
                                                "Ocurrio un error inesperado en el servidor.",
                                                request.getRequestURI(),
                                                List.of()));
        }

        private String formatFieldError(FieldError error) {
                return error.getField() + ": " + error.getDefaultMessage();
        }
}
