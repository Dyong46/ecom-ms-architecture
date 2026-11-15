package com.duong.module.caching.lua;

import com.duong.module.caching.core.CacheKeys;
import com.duong.module.caching.core.CacheScope;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.Objects;

/** Executes application-owned scripts with namespaced keys and separate arguments. */
public final class RedisLuaExecutor {
    private final StringRedisTemplate redis;
    private final CacheKeys cacheKeys;

    public RedisLuaExecutor(StringRedisTemplate redis, CacheKeys cacheKeys) {
        this.redis = redis;
        this.cacheKeys = cacheKeys;
    }

    /** Create once as a Spring bean to reuse Spring's cached script SHA. */
    public static <T> RedisScript<T> loadScript(String classpathLocation, Class<T> resultType) {
        if (classpathLocation == null || classpathLocation.isBlank()) {
            throw new IllegalArgumentException("Lua classpath location is required");
        }
        Objects.requireNonNull(resultType, "Lua result type");
        var resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            throw new IllegalArgumentException("Lua classpath resource does not exist");
        }
        var script = new DefaultRedisScript<T>();
        script.setLocation(resource);
        script.setResultType(resultType);
        script.getSha1();
        return script;
    }

    public <T> T execute(RedisScript<T> script, CacheScope scope, String region,
                         String group, List<String> keys, String... arguments) {
        Objects.requireNonNull(script, "Lua script");
        Objects.requireNonNull(keys, "Lua keys");
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("Lua requires at least one key");
        }
        Objects.requireNonNull(arguments, "Lua arguments");
        for (String argument : arguments) {
            Objects.requireNonNull(argument, "Lua argument must not be null");
        }
        var redisKeys = keys.stream()
                .map(key -> cacheKeys.luaKey(scope, region, group, key))
                .toList();
        return redis.execute(script, redisKeys, (Object[]) arguments);
    }
}
