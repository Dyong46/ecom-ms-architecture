package com.duong.module.web.exception.advice;

import com.duong.module.common.constant.ErrorCode;
import com.duong.module.common.exception.BaseApiException;
import com.duong.module.web.payload.response.BaseResponse;
import com.duong.module.web.payload.response.ErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Shared HTTP error boundary. Business decisions remain in the consuming service. */
@Slf4j
@RestController
@RestControllerAdvice
public class ExceptionControllerAdvice extends ResponseEntityExceptionHandler implements ErrorController {

    private static final String INTERNAL_MESSAGE = "An unexpected error occurred";

    @ExceptionHandler(BaseApiException.class)
    public ResponseEntity<Object> handleBaseApiException(BaseApiException exception) {
        var annotation = AnnotatedElementUtils.findMergedAnnotation(exception.getClass(), ResponseStatus.class);
        var status = annotation == null ? HttpStatus.INTERNAL_SERVER_ERROR : annotation.code();
        if (status.is5xxServerError()) {
            log.error("Request processing failure", exception);
            return response(status, INTERNAL_MESSAGE, defaultCode(status), Map.of());
        }
        var extra = new LinkedHashMap<String, Object>();
        if (exception.extraData() != null && !Map.of().equals(exception.extraData())) {
            extra.put("details", exception.extraData());
        }
        String code = StringUtils.hasText(exception.errorCode()) && !ErrorCode.UNKNOWN.equals(exception.errorCode())
                ? exception.errorCode() : defaultCode(status);
        return response(status, exception.getMessage(), code, extra);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Object> handleEntityNotFound(EntityNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), ErrorCode.NOT_FOUND, Map.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException exception) {
        var errors = new LinkedHashMap<String, String>();
        exception.getConstraintViolations().forEach(violation ->
                errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return response(HttpStatus.BAD_REQUEST, "Request validation failed", ErrorCode.INVALID_INPUT_DATA,
                Map.of("errors", errors));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        var errors = new LinkedHashMap<String, String>();
        exception.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            errors.putIfAbsent(field, error.getDefaultMessage());
        });
        return handleExceptionInternal(exception,
                envelope(status, "Request validation failed", ErrorCode.INVALID_INPUT_DATA, Map.of("errors", errors)),
                headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            log.error("Request processing failure", exception);
        }
        Object responseBody = body instanceof BaseResponse<?> ? body
                : envelope(status, safeMessage(exception, status), defaultCode(status), Map.of());
        var responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        return super.handleExceptionInternal(exception, responseBody, responseHeaders, status, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception) {
        log.error("Unexpected request failure", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_MESSAGE, ErrorCode.UNKNOWN, Map.of());
    }

    /** Servlet errors may carry only a status, with no exception attached. */
    @RequestMapping("${server.error.path:${error.path:/error}}")
    public ResponseEntity<Object> error(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = value instanceof Integer code ? HttpStatus.resolve(code) : null;
        if (status == null || !status.isError()) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (status.is5xxServerError()
                && request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable exception) {
            log.error("Servlet request failure", exception);
        }
        return response(status, status.is5xxServerError() ? INTERNAL_MESSAGE : status.getReasonPhrase(),
                defaultCode(status), Map.of());
    }

    private static String safeMessage(Exception exception, HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return INTERNAL_MESSAGE;
        }
        if (exception instanceof org.springframework.http.converter.HttpMessageNotReadableException) {
            return "Failed to read request";
        }
        var knownStatus = HttpStatus.resolve(status.value());
        return knownStatus == null ? "Request rejected" : knownStatus.getReasonPhrase();
    }

    private static String defaultCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorCode.INVALID_INPUT_DATA;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.ACCESS_DENIED;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.HTTP_REQUEST_METHOD_NOT_SUPPORTED;
            case 409 -> "CONFLICT";
            default -> status.is5xxServerError() ? ErrorCode.UNKNOWN : "HTTP_" + status.value();
        };
    }

    private static BaseResponse<ErrorResponse> envelope(
            HttpStatusCode status, String message, String code, Map<String, ?> additionalData) {
        var extra = new LinkedHashMap<String, Object>(additionalData);
        var knownStatus = HttpStatus.resolve(status.value());
        extra.put("message", StringUtils.hasText(message) ? message
                : knownStatus == null ? "Request rejected" : knownStatus.getReasonPhrase());
        return BaseResponse.of(ErrorResponse.create().errorCode(code).extraData(extra));
    }

    private static ResponseEntity<Object> response(
            HttpStatusCode status, String message, String code, Map<String, ?> extra) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(envelope(status, message, code, extra));
    }
}
