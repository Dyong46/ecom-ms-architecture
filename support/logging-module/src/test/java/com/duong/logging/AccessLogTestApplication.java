package com.duong.logging;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({AccessLogTestApplication.Endpoints.class, AccessLogTestApplication.ServletRejection.class,
        AccessLogTestApplication.ReactiveRejection.class})
class AccessLogTestApplication {
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ServletRejection {
        @Bean
        FilterRegistrationBean<jakarta.servlet.Filter> rejectionFilter() {
            var filter = new FilterRegistrationBean<jakarta.servlet.Filter>((request, response, chain) -> {
                if (((jakarta.servlet.http.HttpServletRequest) request).getRequestURI().equals("/blocked")) {
                    ((jakarta.servlet.http.HttpServletResponse) response).setStatus(403);
                } else {
                    chain.doFilter(request, response);
                }
            });
            return filter;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    static class ReactiveRejection {
        @Bean
        WebFilter rejectionFilter() {
            return (exchange, chain) -> {
                if (exchange.getRequest().getPath().value().equals("/blocked")) {
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return Mono.empty();
                }
                return chain.filter(exchange);
            };
        }
    }

    @RestController
    static class Endpoints {
        @GetMapping("/ok")
        String ok() {
            return "ok";
        }

        @PostMapping("/body")
        String body(@RequestBody String body) {
            return "accepted";
        }

        @GetMapping("/not-implemented")
        ResponseEntity<Void> notImplemented() {
            return ResponseEntity.status(501).build();
        }

        @GetMapping("/unauthorized")
        ResponseEntity<Void> unauthorized() {
            return ResponseEntity.status(401).build();
        }

        @GetMapping("/failure")
        String failure() {
            throw new IllegalStateException("Synthetic server failure");
        }

        @GetMapping("/async")
        CompletableFuture<String> async() {
            return CompletableFuture.supplyAsync(() -> "async response");
        }
    }
}
