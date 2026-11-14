package com.duong.module.caching.core;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/** Storage only: callers own token acquisition and any distributed refresh coordination. */
public final class AccessTokenCache {
    private static final String REGION = "oauth-tokens";
    private final RedisJsonCache cache;
    private final Clock clock;
    private final Duration expiryMargin;

    public AccessTokenCache(RedisJsonCache cache, Clock clock, Duration expiryMargin) {
        if (expiryMargin == null || expiryMargin.isNegative()) {
            throw new IllegalArgumentException("token expiry margin must not be negative");
        }
        this.cache = cache;
        this.clock = clock;
        this.expiryMargin = expiryMargin;
    }

    public Optional<String> getValidToken(OAuthTokenKey identity) {
        return cache.get(CacheScope.GLOBAL, REGION, identity.cacheKey(), CachedAccessToken.class)
                .filter(token -> token.expiresAt().isAfter(clock.instant().plus(expiryMargin)))
                .map(CachedAccessToken::accessToken);
    }

    /** Returns false without writing if the token is already too close to expiry. */
    public boolean put(OAuthTokenKey identity, CachedAccessToken token) {
        var ttl = Duration.between(clock.instant(), token.expiresAt()).minus(expiryMargin);
        if (ttl.isNegative() || ttl.isZero() || ttl.toMillis() < 1) { return false; }
        cache.put(CacheScope.GLOBAL, REGION, identity.cacheKey(), token, ttl);
        return true;
    }

    public boolean evict(OAuthTokenKey identity) {
        return cache.evict(CacheScope.GLOBAL, REGION, identity.cacheKey());
    }
}
