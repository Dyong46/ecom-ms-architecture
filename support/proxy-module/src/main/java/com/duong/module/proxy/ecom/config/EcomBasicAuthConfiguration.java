package com.duong.module.proxy.ecom.config;

import feign.auth.BasicAuthRequestInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Child-context configuration of EcomAuthClient only. */
@EnableConfigurationProperties(EcomOAuthProperties.class)
public class EcomBasicAuthConfiguration {
    @Bean
    BasicAuthRequestInterceptor ecomBasicAuthInterceptor(EcomOAuthProperties properties) {
        return new BasicAuthRequestInterceptor(properties.oauthUsername(), properties.oauthPassword());
    }
}
