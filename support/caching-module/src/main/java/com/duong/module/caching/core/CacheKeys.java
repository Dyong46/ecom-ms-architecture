package com.duong.module.caching.core;

import com.duong.module.caching.entity.enumeration.CacheScopeEnum;

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

    public String jsonKey(CacheScopeEnum scope, String region, String key) {
        Objects.requireNonNull(scope, "cache scope");
        CachingProperties.requireName(region, "region");
        if (key == null || key.isBlank()) { throw new IllegalArgumentException("cache key must not be blank"); }
        // Encode arbitrary caller keys so delimiters cannot collide with namespace segments.
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
        return root + (scope == CacheScopeEnum.GLOBAL ? "global" : "service:" + application) + ":json:" + region + ":" + encoded;
    }

    /** Related script keys share a Redis Cluster slot within their scope and group. */
    public String luaKey(CacheScopeEnum scope, String region, String group, String key) {
        Objects.requireNonNull(scope, "cache scope");
        CachingProperties.requireName(region, "region");
        if (group == null || group.isBlank() || key == null || key.isBlank()) {
            throw new IllegalArgumentException("Lua group and key must not be blank");
        }
        String owner = scope == CacheScopeEnum.GLOBAL ? "global" : "service:" + application;
        String prefix = root + owner + ":lua:" + region + ":";
        String tag = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((prefix + group).getBytes(StandardCharsets.UTF_8));
        String encodedKey = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(key.getBytes(StandardCharsets.UTF_8));
        return prefix + "{" + tag + "}:" + encodedKey;
    }

    public String springPrefix(String region) {
        CachingProperties.requireName(region, "region");
        return root + "service:" + application + ":spring:" + region + ":";
    }
}
