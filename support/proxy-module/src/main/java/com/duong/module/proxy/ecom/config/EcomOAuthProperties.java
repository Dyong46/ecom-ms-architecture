package com.duong.module.proxy.ecom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound only by applications opting into the shared Ecom OAuth integration. */
@ConfigurationProperties("integration-config.ecom")
public record EcomOAuthProperties(String oauthBaseUrl, String oauthRealm, String oauthUsername, String oauthPassword) {
    public EcomOAuthProperties {
        if (blank(oauthBaseUrl) || blank(oauthRealm) || blank(oauthUsername) || blank(oauthPassword)) {
            throw new IllegalArgumentException("Ecom OAuth URL, realm, client ID and client secret are required");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public String toString() {
        return "EcomOAuthProperties[credentials=REDACTED]";
    }
}
