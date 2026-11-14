package com.duong.module.caching.config;

import com.duong.module.caching.core.CacheKeys;
import com.duong.module.caching.core.AccessTokenCache;
import java.time.Clock;
import java.time.Duration;
import com.duong.module.caching.core.RedisJsonCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import java.util.HashMap;

@Configuration(proxyBeanMethods = false)
@EnableCaching
@EnableConfigurationProperties(CachingProperties.class)
public class CachingModuleConfiguration {
    @Bean
    @ConditionalOnMissingBean
    CacheKeys cacheKeys(CachingProperties properties, @Value("${spring.application.name}") String application) {
        return new CacheKeys(properties, application);
    }

    @Bean
    @ConditionalOnMissingBean
    RedisJsonCache redisJsonCache(RedisConnectionFactory factory, CacheKeys keys, CachingProperties properties) {
        // Dedicated String template: no dependency on consumer RedisTemplate serializer choices.
        return new RedisJsonCache(new StringRedisTemplate(factory), CacheJson.typedMapper(), keys, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    AccessTokenCache accessTokenCache(RedisJsonCache cache) {
        return new AccessTokenCache(cache, Clock.systemUTC(), Duration.ofSeconds(30));
    }

    @Bean("ecomRedisCacheManager")
    @Primary
    @ConditionalOnMissingBean(name = "ecomRedisCacheManager")
    RedisCacheManager redisCacheManager(RedisConnectionFactory factory, CacheKeys keys, CachingProperties properties) {
        var defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.defaultTtl()).disableCachingNullValues()
                .computePrefixWith(keys::springPrefix)
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(CacheJson.springSerializer()));
        var regions = new HashMap<String, RedisCacheConfiguration>();
        properties.regions().forEach((name, policy) -> regions.put(name, defaults.entryTtl(properties.ttl(name))));
        return RedisCacheManager.builder(RedisCacheWriter.nonLockingRedisCacheWriter(factory, BatchStrategies.scan(500)))
                .cacheDefaults(defaults).withInitialCacheConfigurations(regions).enableStatistics().build();
    }

    @Bean("ecomMemoryCacheManager")
    @ConditionalOnMissingBean(name = "ecomMemoryCacheManager")
    CaffeineCacheManager memoryCacheManager(CachingProperties properties) {
        var manager = new CaffeineCacheManager();
        manager.setAllowNullValues(false);
        manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(properties.defaultTtl())
                .maximumSize(properties.memoryMaxSize()).recordStats());
        properties.regions().forEach((name, policy) -> manager.registerCustomCache(name,
                Caffeine.newBuilder().expireAfterWrite(properties.ttl(name))
                        .maximumSize(properties.maxSize(name)).recordStats().build()));
        return manager;
    }
}
