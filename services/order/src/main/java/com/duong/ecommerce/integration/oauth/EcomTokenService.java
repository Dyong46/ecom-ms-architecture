package com.duong.ecommerce.integration.oauth;

import com.duong.module.proxy.ecom.EcomAuthClient;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/** Acquires machine tokens for Order using its Redis cache policy. */
public final class EcomTokenService {
    private final EcomAuthClient client;
    private final String realm;
    private final Clock clock;
    private final EcomTokenCache cache;

    public EcomTokenService(EcomAuthClient client, String realm, Clock clock, EcomTokenCache cache) {
        this.client = client;
        this.realm = realm;
        this.clock = clock;
        this.cache = cache;
    }

    public synchronized String getAccessToken() {
        var cached = cache.getValidToken();
        if (cached.isPresent()) {
            return cached.get();
        }
        // Measure expiry conservatively from before the network request.
        Instant requestedAt = clock.instant();
        var response = client.oauthToken(realm, Map.of("grant_type", "client_credentials"));
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()
                || response.expiresIn() <= 0 || !"Bearer".equalsIgnoreCase(response.tokenType())) {
            throw new IllegalStateException("Ecom OAuth returned an invalid token response");
        }
        cache.put(response.accessToken(), requestedAt.plusSeconds(response.expiresIn()));
        return response.accessToken();
    }
}
