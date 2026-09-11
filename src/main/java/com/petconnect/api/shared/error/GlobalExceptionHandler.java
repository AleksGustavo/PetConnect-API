package com.petconnect.api.shared.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Converte qualquer exceção num {@link ApiError} no formato único.
 * Nunca expõe stack trace nem mensagem interna de infraestrutura ao cliente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        return build(ex.status(), ex.code(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ApiError> handleValidation(Exception ex) {
        String detail = ex instanceof MethodArgumentNotValidException manv
                ? manv.getBindingResult().getFieldErrors().stream()
                        .map(f -> f.getField() + " " + f.getDefaultMessage())
                        .collect(Collectors.joining("; "))
                : "Requisição inválida.";
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Credenciais ausentes ou inválidas.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Acesso negado.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Método não permitido para este recurso.");
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(Exception ex) {
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Recurso não encontrado.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Erro não tratado", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Erro interno. Tente novamente mais tarde.");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), code, message));
    }
}
