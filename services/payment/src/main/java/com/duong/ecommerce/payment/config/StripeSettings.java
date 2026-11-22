package com.duong.ecommerce.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("payment.stripe")
public record StripeSettings(@DefaultValue("false") boolean enabled, @DefaultValue("") String secretKey,
        @DefaultValue("") String webhookSecret, @DefaultValue("https://api.stripe.com") URI apiBaseUrl,
        @DefaultValue("http://localhost:3000/payment/success") String successUrl,
        @DefaultValue("http://localhost:3000/payment/cancel") String cancelUrl,
        @DefaultValue("2026-11-18.endive") String apiVersion, @DefaultValue("8s") Duration timeout) {
    public StripeSettings {
        if (enabled && (secretKey == null || !secretKey.startsWith("sk_test_")
                || webhookSecret == null || !webhookSecret.startsWith("whsec_"))) {
            throw new IllegalArgumentException("Stripe sandbox requires sk_test_ and whsec_ runtime credentials");
        }
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(15)) > 0) {
            throw new IllegalArgumentException("Stripe timeout must be positive and at most 15 seconds");
        }
        if (!"https".equals(apiBaseUrl.getScheme()) && !("http".equals(apiBaseUrl.getScheme())
                && java.util.Set.of("127.0.0.1", "localhost").contains(apiBaseUrl.getHost()))) {
            throw new IllegalArgumentException("Stripe API requires HTTPS (loopback HTTP allowed for isolated tests)");
        }
        for (String url : new String[]{successUrl, cancelUrl}) {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) && !("http".equals(uri.getScheme())
                    && java.util.Set.of("localhost", "127.0.0.1").contains(uri.getHost()))) {
                throw new IllegalArgumentException("Checkout return URLs require HTTPS or local HTTP");
            }
        }
    }
    @Override public String toString() { return "StripeSettings[enabled=" + enabled + ", credentials=REDACTED]"; }
}
