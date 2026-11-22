package com.duong.module.caching;

import com.duong.module.caching.entity.enumeration.CacheScopeEnum;

import com.duong.module.caching.annotation.EnableCachingModule;
import com.duong.module.caching.config.CacheJson;
import com.duong.module.caching.config.CachingProperties;
import com.duong.module.caching.core.*;
import com.duong.module.caching.lua.RedisLuaExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.duong.module.caching.entity.enumeration.CacheScopeEnum.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Requires a disposable Redis. Never uses FLUSHDB or a shared application's namespace. */
@EnabledIfEnvironmentVariable(named = "CACHE_TEST_REDIS_PORT", matches = "[0-9]+")
class RedisCacheIntegrationTest {
    private ApplicationContextRunner context() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
                .withUserConfiguration(Enabled.class).withPropertyValues(
                        "spring.application.name=order-service",
                        "spring.data.redis.host=127.0.0.1", "spring.data.redis.port=" + System.getenv("CACHE_TEST_REDIS_PORT"),
                        "spring.data.redis.connect-timeout=1s", "spring.data.redis.timeout=1s",
                        "caching-config.namespace=test-" + UUID.randomUUID(),
                        "caching-config.default-ttl=5s", "caching-config.regions.short.ttl=150ms");
    }

    private CachingModuleTest.Product product() {
        return new CachingModuleTest.Product(1, "O'Brien — sản phẩm", LocalDateTime.of(2026, 11, 14, 12, 0));
    }

    @Test
    void storesWholeCollectionsAndSharesOnlyTheExplicitGlobalScope() {
        context().run(ctx -> {
            var cache = ctx.getBean(RedisJsonCache.class);
            var props = ctx.getBean(CachingProperties.class);
            var redis = new StringRedisTemplate(ctx.getBean(RedisConnectionFactory.class));
            var replica = new RedisJsonCache(redis, CacheJson.typedMapper(), new CacheKeys(props, "order-service"), props);
            var otherService = new RedisJsonCache(redis, CacheJson.typedMapper(), new CacheKeys(props, "product-service"), props);
            var products = List.of(product(), new CachingModuleTest.Product(2, "Second", product().updatedAt()));
            cache.put(SERVICE, "catalog", "ü:'key", products);
            assertThat(replica.get(SERVICE, "catalog", "ü:'key", new TypeReference<List<CachingModuleTest.Product>>() {}))
                    .contains(products);
            assertThat(otherService.get(SERVICE, "catalog", "ü:'key", new TypeReference<List<CachingModuleTest.Product>>() {}))
                    .isEmpty();
            cache.put(GLOBAL, "catalog", "1", product());
            assertThat(otherService.get(GLOBAL, "catalog", "1", CachingModuleTest.Product.class)).contains(product());
            assertThat(otherService.evict(GLOBAL, "catalog", "1")).isTrue();
            assertThat(cache.get(GLOBAL, "catalog", "1", CachingModuleTest.Product.class)).isEmpty();
        });
    }

    @Test
    void everyWriteHasTtlAndRegionAndExplicitExpiryActuallyExpire() {
        context().run(ctx -> {
            var cache = ctx.getBean(RedisJsonCache.class);
            var redis = new StringRedisTemplate(ctx.getBean(RedisConnectionFactory.class));
            var keys = ctx.getBean(CacheKeys.class);
            cache.put(SERVICE, "short", "p", product());
            assertThat(redis.getExpire(keys.jsonKey(SERVICE, "short", "p"), TimeUnit.MILLISECONDS)).isBetween(1L, 150L);
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                    assertThat(cache.get(SERVICE, "short", "p", CachingModuleTest.Product.class)).isEmpty());
            cache.put(SERVICE, "catalog", "p", product(), Duration.ofMillis(150));
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                    assertThat(cache.get(SERVICE, "catalog", "p", CachingModuleTest.Product.class)).isEmpty());
            assertThatThrownBy(() -> cache.put(SERVICE, "catalog", "p", product(), Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    void concurrentSetIfAbsentHasOneWinnerAndKeepsExpiry() {
        context().run(ctx -> {
            var cache = ctx.getBean(RedisJsonCache.class);
            var workers = Executors.newFixedThreadPool(6);
            try {
                var jobs = new ArrayList<Callable<Boolean>>();
                for (int i = 0; i < 20; i++) {
                    int value = i;
                    jobs.add(() -> cache.putIfAbsent(GLOBAL, "dedup", "same", value, Duration.ofSeconds(5)));
                }
                int winners = 0;
                for (var result : workers.invokeAll(jobs)) { if (result.get()) { winners++; } }
                assertThat(winners).isEqualTo(1);
                assertThat(cache.get(GLOBAL, "dedup", "same", Integer.class)).isPresent();
                var redis = new StringRedisTemplate(ctx.getBean(RedisConnectionFactory.class));
                assertThat(redis.getExpire(ctx.getBean(CacheKeys.class).jsonKey(GLOBAL, "dedup", "same"))).isPositive();
            } finally { workers.shutdownNow(); }
        });
    }

    @Test
    void springCacheAnnotationsCacheTypedRecordsAndEvictOnlyTheirOwnEntries() {
        context().run(ctx -> {
            var service = ctx.getBean(Catalog.class);
            assertThat(service.find("1")).isEqualTo(service.find("1"));
            assertThat(service.calls()).isEqualTo(1);
            var redis = new StringRedisTemplate(ctx.getBean(RedisConnectionFactory.class));
            assertThat(redis.getExpire(ctx.getBean(CacheKeys.class).springPrefix("catalog") + "1")).isPositive();
            service.evict("1");
            service.find("1");
            assertThat(service.calls()).isEqualTo(2);
            var cache = ctx.getBean(RedisJsonCache.class);
            cache.put(SERVICE, "catalog", "1", product());
            service.clear();
            assertThat(cache.get(SERVICE, "catalog", "1", CachingModuleTest.Product.class)).contains(product());
        });
    }

    @Test
    void machineTokenUsesSharedRedisWithIdentityIsolationAndConservativeExpiry() {
        context().run(ctx -> {
            var tokens = ctx.getBean(AccessTokenCache.class);
            var identity = new OAuthTokenKey("https://idp.test/realms/ecom", "order-service", "read");
            assertThat(tokens.put(identity, new CachedAccessToken("fixture-token", java.time.Instant.now().plusSeconds(120)))).isTrue();
            assertThat(tokens.getValidToken(identity)).contains("fixture-token");
            assertThat(tokens.getValidToken(new OAuthTokenKey(identity.issuer(), "other-service", "read"))).isEmpty();
            var redis = new StringRedisTemplate(ctx.getBean(RedisConnectionFactory.class));
            var ttl = redis.getExpire(ctx.getBean(CacheKeys.class).jsonKey(GLOBAL, "oauth-tokens", identity.cacheKey()));
            assertThat(ttl).isBetween(1L, 90L);
            assertThat(tokens.evict(identity)).isTrue();
            assertThat(tokens.getValidToken(identity)).isEmpty();
        });
    }

    @Test
    void invalidJsonIsAnErrorNotACacheMissAndDoesNotExposePayload() {
        context().run(ctx -> {
            var redis = new StringRedisTemplate(ctx.getBean(RedisConnectionFactory.class));
            redis.opsForValue().set(ctx.getBean(CacheKeys.class).jsonKey(SERVICE, "catalog", "bad"),
                    "{private-cached-payload", Duration.ofSeconds(5));
            assertThatThrownBy(() -> ctx.getBean(RedisJsonCache.class).get(SERVICE, "catalog", "bad", String.class))
                    .isInstanceOf(CacheSerializationException.class).hasNoCause()
                    .hasMessageNotContaining("private-cached-payload");
        });
    }

    @Test
    void luaReceivesAllKeysAndArgumentsAndUsesItsOwnNamespaceAndExpiry() {
        context().run(ctx -> {
            var executor = ctx.getBean(RedisLuaExecutor.class);
            var script = RedisLuaExecutor.loadScript("redis/write-pair.lua", List.class);
            String value = "O'Brien — dữ liệu\n'quoted'";
            assertThat(executor.execute(script, GLOBAL, "pairs", "pair-1", List.of("left", "right"), value, "second", "5000"))
                    .containsExactly(value, "second");
            var redis = new StringRedisTemplate(ctx.getBean(RedisConnectionFactory.class));
            var keys = ctx.getBean(CacheKeys.class);
            String key = keys.luaKey(GLOBAL, "pairs", "pair-1", "left");
            assertThat(redis.getExpire(key)).isPositive();
            assertThat(redis.opsForValue().get(keys.luaKey(SERVICE, "pairs", "pair-1", "left"))).isNull();
            assertThat(redis.opsForValue().get(keys.jsonKey(GLOBAL, "pairs", "left"))).isNull();
            redis.delete(key);
            // Reuse the script instance with different arguments.
            assertThat(executor.execute(script, GLOBAL, "pairs", "pair-1", List.of("left", "right"), "again", "second", "150"))
                    .containsExactly("again", "second");
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(redis.opsForValue().get(key)).isNull());
        });
    }

    @Test
    void luaCheckAndUpdateHasOneWinnerUnderConcurrentRequests() {
        context().run(ctx -> {
            var executor = ctx.getBean(RedisLuaExecutor.class);
            var script = RedisLuaExecutor.loadScript("redis/increment-if-below.lua", Long.class);
            var workers = Executors.newFixedThreadPool(6);
            try {
                var jobs = new ArrayList<Callable<Long>>();
                for (int i = 0; i < 20; i++) {
                    jobs.add(() -> executor.execute(script, GLOBAL, "limits", "fixture", List.of("count"), "1", "5000"));
                }
                long winners = 0;
                for (var result : workers.invokeAll(jobs)) {
                    winners += result.get();
                }
                assertThat(winners).isEqualTo(1);
                var redis = new StringRedisTemplate(ctx.getBean(RedisConnectionFactory.class));
                var key = ctx.getBean(CacheKeys.class).luaKey(GLOBAL, "limits", "fixture", "count");
                assertThat(redis.opsForValue().get(key)).isEqualTo("1");
            } finally {
                workers.shutdownNow();
            }
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCachingModule
    static class Enabled {
        @Bean Catalog catalog() { return new Catalog(); }
    }

    static class Catalog {
        private final AtomicInteger count = new AtomicInteger();
        @Cacheable(cacheNames = "catalog", cacheManager = "ecomRedisCacheManager", key = "#p0")
        public CachingModuleTest.Product find(String id) {
            count.incrementAndGet();
            return new CachingModuleTest.Product(Integer.parseInt(id), "Product", LocalDateTime.of(2026, 11, 14, 12, 0));
        }
        @CacheEvict(cacheNames = "catalog", cacheManager = "ecomRedisCacheManager", key = "#p0")
        public void evict(String id) {}
        @CacheEvict(cacheNames = "catalog", cacheManager = "ecomRedisCacheManager", allEntries = true)
        public void clear() {}
        public int calls() { return count.get(); }
    }
}
