package com.duong.ecommerce.integration.oauth;

import com.duong.module.caching.core.AccessTokenCache;
import com.duong.module.caching.core.CachedAccessToken;
import com.duong.module.caching.core.OAuthTokenKey;
import com.duong.module.proxy.ecom.EcomAuthClient;


import com.duong.module.proxy.ecom.config.EcomOAuthProperties;
import com.duong.module.proxy.ecom.response.EcomTokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisEcomTokenCacheTest {
    private static final EcomOAuthProperties PROPERTIES = new EcomOAuthProperties(
            "http://idp.example.test/", "micro-services", "ecom-services", "fixture-secret");

    @Test
    void cacheFailureFetchesFreshTokenAndWriteFailureDoesNotDiscardIt() {
        var cache = mock(AccessTokenCache.class);
        var client = mock(EcomAuthClient.class);
        when(cache.getValidToken(any())).thenThrow(new RedisConnectionFailureException("fixture unavailable"));
        when(cache.put(any(), any())).thenThrow(new RedisConnectionFailureException("fixture unavailable"));
        when(client.oauthToken("micro-services", Map.of("grant_type", "client_credentials")))
                .thenReturn(new EcomTokenResponse("fresh-fixture-token", 300, "Bearer"));
        var now = Instant.parse("2026-11-15T00:00:00Z");
        var service = new EcomTokenService(client, "micro-services", Clock.fixed(now, ZoneOffset.UTC),
                new RedisEcomTokenCache(cache, PROPERTIES));
        assertThat(service.getAccessToken()).isEqualTo("fresh-fixture-token");
        var identity = new OAuthTokenKey("http://idp.example.test/realms/micro-services", "ecom-services", "");
        verify(cache).put(identity, new CachedAccessToken("fresh-fixture-token", now.plusSeconds(300)));
        verify(client).oauthToken("micro-services", Map.of("grant_type", "client_credentials"));
    }

    @Test
    void validSharedCacheAvoidsAuthRequestAndAuthFailureOnMissPropagates() {
        var cache = mock(AccessTokenCache.class);
        var client = mock(EcomAuthClient.class);
        when(cache.getValidToken(any())).thenReturn(Optional.of("cached-fixture-token"));
        var service = new EcomTokenService(client, "micro-services", Clock.systemUTC(), new RedisEcomTokenCache(cache, PROPERTIES));
        assertThat(service.getAccessToken()).isEqualTo("cached-fixture-token");
        verifyNoInteractions(client);
        when(cache.getValidToken(any())).thenReturn(Optional.empty());
        when(client.oauthToken(anyString(), anyMap())).thenThrow(new IllegalStateException("auth fixture failure"));
        assertThatThrownBy(service::getAccessToken).hasMessage("auth fixture failure");
        verify(cache, never()).put(any(), any());
    }
}
