package com.duong.module.caching;

import com.duong.module.caching.entity.enumeration.CacheScopeEnum;

import com.duong.module.caching.core.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessTokenCacheTest {
    @Test
    void tokensDoNotCrossIssuersClientsOrRequestedScopes() {
        var key = new OAuthTokenKey("https://idp.test/realm", "order", "read write read");
        assertThat(key).isEqualTo(new OAuthTokenKey(key.issuer(), "order", "write read"));
        assertThat(key.cacheKey()).isNotEqualTo(new OAuthTokenKey(key.issuer(), "payment", key.scope()).cacheKey())
                .isNotEqualTo(new OAuthTokenKey("https://other.test", "order", key.scope()).cacheKey())
                .isNotEqualTo(new OAuthTokenKey(key.issuer(), "order", "read").cacheKey());
    }

    @Test
    void expiredAndNearlyExpiredTokensAreNotServedEvenIfRedisStillContainsThem() {
        var redis = mock(RedisJsonCache.class);
        var now = Instant.parse("2026-11-14T00:00:00Z");
        var identity = new OAuthTokenKey("https://idp.test", "order", "");
        var tokens = new AccessTokenCache(redis, Clock.fixed(now, ZoneOffset.UTC), Duration.ofSeconds(30));
        for (long seconds : new long[]{-10, 0, 30, 31}) {
            var token = new CachedAccessToken("dummy-test-token", now.plusSeconds(seconds));
            when(redis.get(CacheScopeEnum.GLOBAL, "oauth-tokens", identity.cacheKey(), CachedAccessToken.class)).thenReturn(Optional.of(token));
            assertThat(tokens.getValidToken(identity).isPresent()).isEqualTo(seconds > 30);
            assertThat(tokens.put(identity, token)).isEqualTo(seconds > 30);
            assertThat(token.toString()).doesNotContain("dummy-test-token");
        }
        verify(redis, times(1)).put(eq(CacheScopeEnum.GLOBAL), eq("oauth-tokens"), eq(identity.cacheKey()),
                any(CachedAccessToken.class), eq(Duration.ofSeconds(1)));
    }

    @Test
    void redisFailureIsPropagatedSoCallerCanChooseItsOwnFallbackPolicy() {
        var redis = mock(RedisJsonCache.class);
        var identity = new OAuthTokenKey("https://idp.test", "order", "");
        when(redis.get(CacheScopeEnum.GLOBAL, "oauth-tokens", identity.cacheKey(), CachedAccessToken.class))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("test unavailable"));
        var tokens = new AccessTokenCache(redis, Clock.systemUTC(), Duration.ofSeconds(30));
        assertThatThrownBy(() -> tokens.getValidToken(identity))
                .isInstanceOf(org.springframework.data.redis.RedisConnectionFailureException.class);
    }
}
