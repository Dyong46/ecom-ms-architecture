package com.duong.module.proxy.config;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** A Feign child-context configuration, deliberately not component-scanned. */
@EnableConfigurationProperties(ProxyHttpProperties.class)
public class ProxyFeignConfiguration {
    @Bean
    Request.Options proxyRequestOptions(ProxyHttpProperties properties) {
        return new Request.Options(properties.connectTimeout(), properties.readTimeout(), false);
    }

    @Bean
    Retryer proxyRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    Logger.Level proxyLogLevel() {
        return Logger.Level.NONE;
    }
}
