package com.duong.module.proxy.ecom;


import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/** Each process caches a token for the shared client identity; no user-token relay. */
public final class EcomTokenService {
    private final EcomAuthClient client;
    private final String realm;
    private final Clock clock;
    private String token;
    private Instant refreshAt = Instant.MIN;

    public EcomTokenService(EcomAuthClient client, String realm, Clock clock) {
        this.client = client;
        this.realm = realm;
        this.clock = clock;
    }

    public synchronized String getAccessToken() {
        if (token != null && clock.instant().isBefore(refreshAt)) {
            return token;
        }
        // Measure expiry conservatively from before the network request.
        Instant requestedAt = clock.instant();
        var response = client.oauthToken(realm, Map.of("grant_type", "client_credentials"));
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()
                || response.expiresIn() <= 0 || !"Bearer".equalsIgnoreCase(response.tokenType())) {
            throw new IllegalStateException("Ecom OAuth returned an invalid token response");
        }
        token = response.accessToken();
        refreshAt = requestedAt.plusSeconds(response.expiresIn() - Math.min(30, response.expiresIn() / 10));
        return token;
    }
}
