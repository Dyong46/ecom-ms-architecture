package com.duong.ecommerce.order.integration.oauth;

import java.time.Instant;
import java.util.Optional;

/** Cache for one configured Ecom machine identity. */
public interface EcomTokenCache {
    Optional<String> getValidToken();
    void put(String accessToken, Instant expiresAt);
}
