package com.duong.module.proxy.ecom.config;

import com.duong.module.proxy.ecom.EcomAuthClient;
import com.duong.module.proxy.ecom.EcomTokenService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EcomOAuthProperties.class)
@EnableFeignClients(clients = EcomAuthClient.class)
public class EcomAuthConfiguration {
    @Bean
    EcomTokenService ecomTokenService(EcomAuthClient client, EcomOAuthProperties properties) {
        return new EcomTokenService(client, properties.oauthRealm(), Clock.systemUTC());
    }
}
