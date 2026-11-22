package com.duong.module.caching.core;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Different issuers, clients or requested scopes must not share a machine token. */
public record OAuthTokenKey(String issuer, String clientId, String scope) {
    public OAuthTokenKey {
        if (issuer == null || issuer.isBlank() || clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("OAuth token issuer and client ID are required");
        }
        scope = scope == null || scope.isBlank() ? "" : Arrays.stream(scope.trim().split("\\s+"))
                .distinct().sorted().collect(Collectors.joining(" "));
    }

    public String cacheKey() {
        return issuer.length() + ":" + issuer + clientId.length() + ":" + clientId + scope.length() + ":" + scope;
    }
}
