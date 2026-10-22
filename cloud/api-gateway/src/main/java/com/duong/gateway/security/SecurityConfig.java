package com.duong.gateway.security;

import com.duong.gateway.config.GatewaySecurityProperties;
import com.duong.gateway.handler.ProblemDetailsHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity serverHttpSecurity,
            ProblemDetailsHandler problems,
            GatewaySecurityProperties properties) {
        serverHttpSecurity.authorizeExchange(exchange -> {
                    exchange.pathMatchers(properties.getPublicPaths().toArray(String[]::new)).permitAll();
                    exchange.pathMatchers(HttpMethod.GET,
                            properties.getPublicGetPaths().toArray(String[]::new)).permitAll();
                    if (properties.getCallback().isEnabled()) {
                        exchange.pathMatchers(properties.getCallback().getPathPattern()).permitAll();
                    }
                    exchange.anyExchange().authenticated();
                })
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
