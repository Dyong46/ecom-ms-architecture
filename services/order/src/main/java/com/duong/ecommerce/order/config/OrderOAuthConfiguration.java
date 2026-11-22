package com.duong.ecommerce.order.config;

import com.duong.ecommerce.order.entity.Order;
import com.duong.ecommerce.order.integration.oauth.EcomTokenCache;
import com.duong.ecommerce.order.integration.oauth.EcomTokenService;
import com.duong.ecommerce.order.integration.oauth.RedisEcomTokenCache;

import com.duong.module.caching.annotation.EnableCachingModule;
import com.duong.module.caching.core.AccessTokenCache;
import com.duong.module.proxy.annotation.EnableEcomAuth;
import com.duong.module.proxy.ecom.EcomAuthClient;
import com.duong.module.proxy.ecom.config.EcomOAuthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Order owns composition of independent HTTP and cache capabilities. */
@Configuration(proxyBeanMethods = false)
@EnableCachingModule
@EnableEcomAuth
public class OrderOAuthConfiguration {
    @Bean
    EcomTokenCache ecomTokenCache(AccessTokenCache cache, EcomOAuthProperties properties) {
        return new RedisEcomTokenCache(cache, properties);
    }

    @Bean
    EcomTokenService ecomTokenService(EcomAuthClient client, EcomOAuthProperties properties, EcomTokenCache cache) {
        return new EcomTokenService(client, properties.oauthRealm(), Clock.systemUTC(), cache);
    }
}
