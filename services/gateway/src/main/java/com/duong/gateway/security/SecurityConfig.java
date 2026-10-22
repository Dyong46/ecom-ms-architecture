package com.duong.gateway.security;

import com.duong.gateway.handler.ProblemDetailsHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity serverHttpSecurity, ProblemDetailsHandler problems) {
        serverHttpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/actuator/health", "/actuator/info")
                        .permitAll()
                        .anyExchange()
                        .authenticated()
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((exchange, ex) -> {
                            exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                            return problems.write(exchange, HttpStatus.UNAUTHORIZED);
                        })
                        .accessDeniedHandler((exchange, ex) -> problems.write(exchange, HttpStatus.FORBIDDEN)))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((exchange, ex) -> {
                            exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE,
                                    "Bearer error=\"invalid_token\"");
                            return problems.write(exchange, HttpStatus.UNAUTHORIZED);
                        })
                        .accessDeniedHandler((exchange, ex) -> problems.write(exchange, HttpStatus.FORBIDDEN)));

        return serverHttpSecurity.build();
    }
}
