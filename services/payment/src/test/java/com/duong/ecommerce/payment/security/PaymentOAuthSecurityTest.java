package com.duong.ecommerce.payment.security;

import com.duong.ecommerce.payment.config.PaymentSecurityConfig;
import com.duong.ecommerce.payment.controller.InternalPaymentController;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalPaymentController.class,
        properties = "spring.cloud.config.enabled=false")
@ContextConfiguration(classes = {InternalPaymentController.class, PaymentSecurityConfig.class, PaymentOAuthSecurityTest.Keys.class})
class PaymentOAuthSecurityTest {
    private static final String ISSUER = "https://keycloak.example.test/realms/micro-services";
    private static final KeyPair KEY = generateKey();
    private static final String BODY = """
            {"amount":20.00,"paymentMethod":"PAYPAL","orderId":7,"orderReference":"ORDER-7",
            "customer":{"id":"c1","firstname":"Ada","lastname":"Lovelace","email":"ada@example.test"}}
            """;
    @Autowired MockMvc mvc;
    @MockBean
    com.duong.ecommerce.payment.service.PaymentCreationService service;
    @MockBean
    com.duong.ecommerce.payment.service.PaymentService payments;

    @Test
    void internalListingRequiresTheAllowedMachineClient() throws Exception {
        mvc.perform(get("/internal/payments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/payments").header("Authorization", "Bearer " + token(KEY, ISSUER, "other-client", 300)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(payments);
        when(payments.findAll(any())).thenReturn(org.springframework.data.domain.Page.empty());
        mvc.perform(get("/internal/payments").header("Authorization", "Bearer " + token(KEY, ISSUER, "ecom-services", 300)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty());
        verify(payments).findAll(any());
    }

    @Test
    void formerPublicPaymentPathsCannotBeUsedWithOrWithoutMachineToken() throws Exception {
        for (String path : new String[]{"/api/v1/payments", "/api/v1/payments/callbacks/test"}) {
            mvc.perform(get(path)).andExpect(status().isForbidden());
            mvc.perform(post(path).header("Authorization", "Bearer " + token(KEY, ISSUER, "ecom-services", 300))
                            .header("Idempotency-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service, payments);
    }

    @Test
    void requiresBearerTokenBeforeExecutingPayment() throws Exception {
        mvc.perform(post("/internal/payments").header("Idempotency-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void acceptsSignedEcomTokenWithoutCsrfAndExecutesPayment() throws Exception {
        when(service.create(org.mockito.ArgumentMatchers.eq("test-key"), any())).thenReturn(42);
        mvc.perform(post("/internal/payments").header("Idempotency-Key", "test-key").header("Authorization", "Bearer " + token(KEY, ISSUER, "ecom-services", 300))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(42));
        verify(service).create(org.mockito.ArgumentMatchers.eq("test-key"), any());
    }

    @Test
    void rejectsAnotherClientAndMissingClientClaim() throws Exception {
        for (String client : new String[]{"micro-services-api", ""}) {
            mvc.perform(post("/internal/payments").header("Idempotency-Key", "test-key").header("Authorization", "Bearer " + token(KEY, ISSUER, client, 300))
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidSignatureExpiredTokenWrongIssuerAndMalformedToken() throws Exception {
        String[] invalid = {token(generateKey(), ISSUER, "ecom-services", 300),
                token(KEY, ISSUER, "ecom-services", -120),
                token(KEY, "https://wrong-issuer.test", "ecom-services", 300), "invalid-token"};
        for (String token : invalid) {
            mvc.perform(post("/internal/payments").header("Idempotency-Key", "test-key").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }

    @Test
    void monitoringDoesNotRequirePaymentCredential() throws Exception {
        // No controller in this slice: 404 establishes that the auth filter did not reject it.
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isNotFound());
    }

    private static KeyPair generateKey() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private static String token(KeyPair key, String issuer, String client, long lifetime) {
        var jwk = new RSAKey.Builder((RSAPublicKey) key.getPublic()).privateKey(key.getPrivate()).keyID("test-key").build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(issuer).subject("service-account-ecom-services")
                .issuedAt(now.minusSeconds(300)).expiresAt(now.plusSeconds(lifetime));
        if (!client.isEmpty()) { claims.claim("azp", client); }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build()))
                .getTokenValue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Keys {
        @Bean
        JwtDecoder jwtDecoder() {
            var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY.getPublic()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
            return decoder;
        }
    }
}
