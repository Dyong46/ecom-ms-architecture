package com.duong.ecommerce.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class PaymentSecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain internalPayments(HttpSecurity http,
            @Value("${payment.security.allowed-client-id:ecom-services}") String clientId) throws Exception {
        return http.securityMatcher("/internal/payments", "/internal/payments/**")
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().access((authentication, context) -> {
                    var principal = authentication.get();
                    return new AuthorizationDecision(principal instanceof JwtAuthenticationToken jwt
                            && principal.isAuthenticated() && !clientId.isBlank()
                            && clientId.equals(jwt.getToken().getClaimAsString("azp")));
                }))
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .build();
    }

    // Business APIs are internal only. Keep explicit monitoring/error paths available.
    @Bean
    @Order(2)
    SecurityFilterChain remainingEndpoints(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**",
                                "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
