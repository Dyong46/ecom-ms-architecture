package com.duong.module.caching;

import com.duong.module.caching.entity.enumeration.CacheScopeEnum;

import com.duong.module.caching.annotation.EnableCachingModule;
import com.duong.module.caching.config.CacheJson;
import com.duong.module.caching.config.CachingProperties;
import com.duong.module.caching.core.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

class CachingModuleTest {
    public record Product(int id, String name, LocalDateTime updatedAt) {}

    static CachingProperties defaults() {
        return new CachingProperties("ecom", "test", Duration.ofMinutes(5), 1000, Map.of());
    }

    @Test
    void namespacesSeparateEnvironmentApplicationScopeAndKeyDelimiters() {
        var order = new CacheKeys(defaults(), "order-service");
        var product = new CacheKeys(defaults(), "product-service");
        var prod = new CacheKeys(new CachingProperties("ecom", "prod", Duration.ofMinutes(1), 10, Map.of()), "order-service");
        assertThat(order.jsonKey(CacheScopeEnum.SERVICE, "catalog", "1"))
                .isNotEqualTo(product.jsonKey(CacheScopeEnum.SERVICE, "catalog", "1"))
                .isNotEqualTo(prod.jsonKey(CacheScopeEnum.SERVICE, "catalog", "1"))
                .isNotEqualTo(order.jsonKey(CacheScopeEnum.GLOBAL, "catalog", "1"))
                .isNotEqualTo(order.springPrefix("catalog") + "1");
        assertThat(order.jsonKey(CacheScopeEnum.GLOBAL, "catalog", "1"))
                .isEqualTo(product.jsonKey(CacheScopeEnum.GLOBAL, "catalog", "1"));
        assertThat(order.jsonKey(CacheScopeEnum.SERVICE, "catalog", "a:b"))
                .isNotEqualTo(order.jsonKey(CacheScopeEnum.SERVICE, "catalog", "a::b"));
        assertThatThrownBy(() -> order.jsonKey(CacheScopeEnum.SERVICE, "bad:region", "1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void springJsonRoundTripsFinalRecordsAndCollectionsWithoutJavaSerialization() {
        var serializer = CacheJson.springSerializer();
        var product = new Product(1, "O'Brien — bàn phím", LocalDateTime.of(2026, 11, 14, 12, 0));
        for (Object value : List.of(product, List.of(product), Map.of("p", product), Set.of(product), "text", 42)) {
            assertThat(serializer.deserialize(serializer.serialize(value))).isEqualTo(value);
        }
    }

    @Test
    void springJsonRejectsTypesOutsideAllowedDtoAndValuePackages() {
        var serializer = CacheJson.springSerializer();
        var encoded = serializer.serialize(new File("/not-a-cache-dto"));
        assertThatThrownBy(() -> serializer.deserialize(encoded)).isInstanceOf(org.springframework.data.redis.serializer.SerializationException.class);
    }

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
                .withUserConfiguration(Enabled.class).withPropertyValues("spring.application.name=order-service");
    }

    @Test
    void optInRegistersBoundedMemoryAndRedisManagersAndAppliesDefaultAndRegionPolicies() {
        context().withPropertyValues("caching-config.default-ttl=2m", "caching-config.memory-max-size=25",
                "caching-config.regions.catalog.ttl=10s", "caching-config.regions.catalog.memory-max-size=3")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed().hasSingleBean(RedisJsonCache.class);
                    var redis = ctx.getBean("ecomRedisCacheManager", RedisCacheManager.class);
                    assertThat(redis.getCacheConfigurations().get("catalog").getTtl()).isEqualTo(Duration.ofSeconds(10));
                    var memory = ctx.getBean("ecomMemoryCacheManager", CaffeineCacheManager.class);
                    var cache = ((CaffeineCache) memory.getCache("catalog")).getNativeCache();
                    assertThat(cache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(3);
                    assertThat(cache.policy().expireAfterWrite().orElseThrow().getExpiresAfter()).isEqualTo(Duration.ofSeconds(10));
                    assertThat(ctx.getBean(CachingProperties.class).defaultTtl()).isEqualTo(Duration.ofMinutes(2));
                });
    }

    @Test
    void invalidConfigFailsStartupInsteadOfCreatingAnUnboundedCache() {
        for (String invalid : List.of("caching-config.default-ttl=0s", "caching-config.default-ttl=-1s",
                "caching-config.memory-max-size=0", "caching-config.regions.catalog.ttl=0s",
                "caching-config.namespace=bad:namespace")) {
            context().withPropertyValues(invalid).run(ctx -> assertThat(ctx).hasFailed());
        }
        context().withPropertyValues("spring.application.name=").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void memoryExpiresAndEvictsWithoutRedisAndDoesNotCacheNull() {
        context().withPropertyValues("caching-config.default-ttl=100ms", "caching-config.memory-max-size=2")
                .run(ctx -> {
                    var cache = (CaffeineCache) ctx.getBean("ecomMemoryCacheManager", CaffeineCacheManager.class).getCache("catalog");
                    cache.put("one", "value");
                    assertThat(cache.get("one", String.class)).isEqualTo("value");
                    await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(cache.get("one")).isNull());
                    for (int i = 0; i < 20; i++) { cache.put(i, i); }
                    cache.getNativeCache().cleanUp();
                    assertThat(cache.getNativeCache().estimatedSize()).isLessThanOrEqualTo(2);
                    assertThatThrownBy(() -> cache.put("null", null)).isInstanceOf(IllegalArgumentException.class);
                });
    }

    @Test
    void dependencyAloneDoesNotRegisterTheModule() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(RedisJsonCache.class).doesNotHaveBean(AccessTokenCache.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCachingModule
    static class Enabled {}
}
