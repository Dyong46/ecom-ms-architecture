package com.duong.gateway.security;

import com.duong.gateway.handler.ProblemDetailsHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Arrays;

/** Internal controllers are reached directly by services, never through Gateway. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class InternalApiBoundaryWebFilter implements WebFilter {
    private final ProblemDetailsHandler problems;

    public InternalApiBoundaryWebFilter(ProblemDetailsHandler problems) {
        this.problems = problems;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        boolean internal = exchange.getRequest().getPath().elements().stream()
                .filter(PathContainer.PathSegment.class::isInstance)
                .map(PathContainer.PathSegment.class::cast)
                .flatMap(segment -> Arrays.stream(segment.valueToMatch().split("/")))
                .anyMatch("internal"::equalsIgnoreCase);
        return internal ? problems.write(exchange, HttpStatus.NOT_FOUND) : chain.filter(exchange);
    }
}
