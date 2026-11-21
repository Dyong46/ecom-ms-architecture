package com.duong.ecommerce.payment.integration.stripe;

import com.duong.ecommerce.payment.config.StripeSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/** Stripe transport only; attempt/retry/settlement policy remains in Payment services. */
@Component
public class StripeCheckoutClient {
    private final StripeSettings settings;
    private final ObjectMapper json;
    private final HttpClient http;
    public StripeCheckoutClient(StripeSettings settings, ObjectMapper json) {
        this.settings = settings; this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(settings.timeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public JsonNode createSession(String key, Map<String, String> parameters) {
        String form = parameters.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return exchange(HttpRequest.newBuilder(settings.apiBaseUrl().resolve("/v1/checkout/sessions"))
                .header("Idempotency-Key", key).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)));
    }
    public JsonNode retrieveSession(String sessionId) {
        if (!sessionId.matches("cs_test_[A-Za-z0-9_]+")) throw new IllegalArgumentException("Invalid sandbox Session ID");
        return exchange(HttpRequest.newBuilder(settings.apiBaseUrl().resolve("/v1/checkout/sessions/" + sessionId)).GET());
    }
    private JsonNode exchange(HttpRequest.Builder builder) {
        if (!settings.enabled()) throw new StripeCallException("STRIPE_DISABLED", true);
        try {
            var response = http.send(builder.timeout(settings.timeout()).header("Authorization", "Bearer " + settings.secretKey())
                    .header("Stripe-Version", settings.apiVersion()).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                // Keep provider messages, request data and credentials out of exception/log payloads.
                throw new StripeCallException("STRIPE_HTTP_" + response.statusCode(), response.statusCode() >= 500
                        || response.statusCode() == 429 || response.statusCode() == 409);
            }
            if (response.statusCode() != 200) throw new StripeCallException("STRIPE_UNEXPECTED_STATUS", true);
            return json.readTree(response.body());
        } catch (StripeCallException exception) { throw exception; }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new StripeCallException("STRIPE_INTERRUPTED", true);
        } catch (Exception exception) { throw new StripeCallException("STRIPE_RESPONSE_UNKNOWN", true); }
    }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    public static final class StripeCallException extends RuntimeException {
        private final boolean retryable;
        public StripeCallException(String code, boolean retryable) { super(code); this.retryable = retryable; }
        public boolean retryable() { return retryable; }
    }
}
