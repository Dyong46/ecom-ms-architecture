package com.duong.module.caching.core;

import java.time.Instant;
import java.util.Objects;

/** Absolute expiry must be calculated conservatively from before the token request. */
public record CachedAccessToken(String accessToken, Instant expiresAt) {
    public CachedAccessToken {
        if (accessToken == null || accessToken.isBlank()) { throw new IllegalArgumentException("access token is required"); }
        Objects.requireNonNull(expiresAt, "token expiry");
    }

    @Override
    public String toString() { return "CachedAccessToken[accessToken=REDACTED, expiresAt=" + expiresAt + "]"; }
}
