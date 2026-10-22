package com.duong.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
@Order(-2)
public class ProblemDetailsHandler implements ErrorWebExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsHandler.class);
    private final ObjectMapper objectMapper;

    public ProblemDetailsHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        var status = ex instanceof ErrorResponse error
                ? error.getStatusCode() : HttpStatus.INTERNAL_SERVER_ERROR;
        if (status.is5xxServerError()) {
            log.error("Gateway request failure", ex);
        }
        if (ex instanceof ErrorResponse error && !exchange.getResponse().isCommitted()) {
            exchange.getResponse().getHeaders().addAll(error.getHeaders());
        }
        return write(exchange, status);
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatusCode status) {
        var response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(new IllegalStateException("Response already committed"));
        }
        var httpStatus = HttpStatus.resolve(status.value());
        var title = httpStatus == null ? "HTTP Error" : httpStatus.getReasonPhrase();
        var body = ProblemDetail.forStatusAndDetail(status,
                status.is5xxServerError() ? "An unexpected error occurred" : title);
        body.setTitle(title);
        body.setInstance(URI.create(exchange.getRequest().getURI().getRawPath()));
        try {
            var bytes = objectMapper.writeValueAsBytes(body);
            response.setStatusCode(status);
            response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (JsonProcessingException ex) {
            return Mono.error(ex);
        }
    }
}
