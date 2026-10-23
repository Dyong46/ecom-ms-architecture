package com.duong.ecommerce.handler;

import com.duong.ecommerce.exception.ProductPurchaseException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(com.duong.module.common.exception.NotFoundException.class)
    public ProblemDetail handleBaseNotFound(com.duong.module.common.exception.NotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Product not found");
    }

    @ExceptionHandler(ProductPurchaseException.class)
    public ProblemDetail handle(ProductPurchaseException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handle(EntityNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        var errors = new LinkedHashMap<String, String>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            var key = error instanceof FieldError fieldError
                    ? fieldError.getField() : error.getObjectName();
            errors.putIfAbsent(key, error.getDefaultMessage());
        });
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected request failure", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            log.error("Request processing failure", ex);
            body = ProblemDetail.forStatusAndDetail(status, "An unexpected error occurred");
        }
        return super.handleExceptionInternal(ex, body, headers, status, request);
    }
}
