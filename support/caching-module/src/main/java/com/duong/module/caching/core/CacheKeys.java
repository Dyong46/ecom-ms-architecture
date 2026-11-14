package com.duong.module.caching.core;

import com.duong.module.caching.config.CachingProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/** Namespaces are isolation conventions, not Redis access control. */
public final class CacheKeys {
    private final String root;
    private final String application;

    public CacheKeys(CachingProperties properties, String application) {
        CachingProperties.requireName(application, "spring.application.name");
        root = properties.namespace() + ":" + properties.environment() + ":v1:";
        this.application = application;
    }

    public String jsonKey(CacheScope scope, String region, String key) {
        Objects.requireNonNull(scope, "cache scope");
        CachingProperties.requireName(region, "region");
        if (key == null || key.isBlank()) { throw new IllegalArgumentException("cache key must not be blank"); }
        // Encode arbitrary caller keys so delimiters cannot collide with namespace segments.
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
        return root + (scope == CacheScope.GLOBAL ? "global" : "service:" + application) + ":json:" + region + ":" + encoded;
    }

    public String springPrefix(String region) {
        CachingProperties.requireName(region, "region");
        return root + "service:" + application + ":spring:" + region + ":";
    }
}
