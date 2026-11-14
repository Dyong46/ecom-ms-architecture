package com.duong.module.caching.core;

import com.duong.module.caching.config.CachingProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Typed JSON cache with TTL on every write. Redis failures propagate, not disguised as misses. */
public final class RedisJsonCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final CacheKeys keys;
    private final CachingProperties properties;

    public RedisJsonCache(StringRedisTemplate redis, ObjectMapper mapper, CacheKeys keys, CachingProperties properties) {
        this.redis = redis;
        this.mapper = mapper.copy();
        this.keys = keys;
        this.properties = properties;
    }

    public <T> Optional<T> get(CacheScope scope, String region, String key, Class<T> type) {
        return read(scope, region, key, mapper.constructType(type));
    }

    public <T> Optional<T> get(CacheScope scope, String region, String key, TypeReference<T> type) {
        return read(scope, region, key, mapper.constructType(type));
    }

    private <T> Optional<T> read(CacheScope scope, String region, String key, JavaType type) {
        String json = redis.opsForValue().get(keys.jsonKey(scope, region, key));
        if (json == null) { return Optional.empty(); }
        try { return Optional.ofNullable(mapper.readValue(json, type)); }
        catch (JsonProcessingException ex) { throw new CacheSerializationException(); }
    }

    public void put(CacheScope scope, String region, String key, Object value) {
        put(scope, region, key, value, properties.ttl(region));
    }

    public void put(CacheScope scope, String region, String key, Object value, Duration ttl) {
        CachingProperties.positiveTtl(ttl);
        redis.opsForValue().set(keys.jsonKey(scope, region, key), json(value), ttl);
    }

    /** Atomic SET NX with TTL. This primitive is not a distributed lock or payment idempotency protocol. */
    public boolean putIfAbsent(CacheScope scope, String region, String key, Object value, Duration ttl) {
        CachingProperties.positiveTtl(ttl);
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(keys.jsonKey(scope, region, key), json(value), ttl));
    }

    public boolean evict(CacheScope scope, String region, String key) {
        return Boolean.TRUE.equals(redis.delete(keys.jsonKey(scope, region, key)));
    }

    private String json(Object value) {
        Objects.requireNonNull(value, "cache value must not be null");
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new CacheSerializationException(); }
    }
}
