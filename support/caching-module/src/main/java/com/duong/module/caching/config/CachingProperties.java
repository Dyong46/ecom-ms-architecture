package com.duong.module.caching.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.time.Duration;
import java.util.Map;

@ConfigurationProperties("caching-config")
public record CachingProperties(
        @DefaultValue("ecom") String namespace,
        @DefaultValue("local") String environment,
        @DefaultValue("5m") Duration defaultTtl,
        @DefaultValue("1000") long memoryMaxSize,
        Map<String, Region> regions) {

    public CachingProperties {
        requireName(namespace, "namespace");
        requireName(environment, "environment");
        positiveTtl(defaultTtl);
        if (memoryMaxSize <= 0) { throw new IllegalArgumentException("memory-max-size must be positive"); }
        regions = regions == null ? Map.of() : Map.copyOf(regions);
        regions.forEach((name, region) -> requireName(name, "region"));
    }

    public Duration ttl(String region) {
        requireName(region, "region");
        var override = regions.get(region);
        return override != null && override.ttl() != null ? override.ttl() : defaultTtl;
    }

    public long maxSize(String region) {
        var override = regions.get(region);
        return override != null && override.memoryMaxSize() != null ? override.memoryMaxSize() : memoryMaxSize;
    }

    public record Region(Duration ttl, Long memoryMaxSize) {
        public Region {
            if (ttl != null) { positiveTtl(ttl); }
            if (memoryMaxSize != null && memoryMaxSize <= 0) {
                throw new IllegalArgumentException("region memory-max-size must be positive");
            }
        }
    }

    public static void positiveTtl(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero() || ttl.toMillis() < 1) {
            throw new IllegalArgumentException("cache TTL must be at least 1ms");
        }
    }

    public static void requireName(String value, String name) {
        if (value == null || !value.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}")) {
            throw new IllegalArgumentException(name + " must contain 1-128 letters, digits, dots, underscores or hyphens");
        }
    }
}
