package com.duong.ecommerce.integration.oauth;

import com.duong.module.caching.core.AccessTokenCache;
import com.duong.module.caching.core.CacheSerializationException;
import com.duong.module.caching.core.CachedAccessToken;
import com.duong.module.caching.core.OAuthTokenKey;
import com.duong.module.proxy.ecom.config.EcomOAuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

import java.time.Instant;
import java.util.Optional;

/** Bridges Ecom token acquisition to the shared Redis cache. */
public final class RedisEcomTokenCache implements EcomTokenCache {
    private static final Logger log = LoggerFactory.getLogger(RedisEcomTokenCache.class);
    private final AccessTokenCache cache;
    private final OAuthTokenKey identity;

    public RedisEcomTokenCache(AccessTokenCache cache, EcomOAuthProperties properties) {
        this.cache = cache;
        String issuer = properties.oauthBaseUrl().replaceAll("/+$", "") + "/realms/" + properties.oauthRealm();
        this.identity = new OAuthTokenKey(issuer, properties.oauthUsername(), "");
    }

    @Override
    public Optional<String> getValidToken() {
        try {
            return cache.getValidToken(identity);
        } catch (DataAccessException | CacheSerializationException exception) {
            log.warn("Ecom token cache read failed; fetching from authorization server errorType={}",
                    exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public void put(String accessToken, Instant expiresAt) {
        try {
            cache.put(identity, new CachedAccessToken(accessToken, expiresAt));
        } catch (DataAccessException | CacheSerializationException exception) {
            log.warn("Ecom token cache write failed; using freshly acquired token errorType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
