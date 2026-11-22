package com.duong.module.proxy.ecom.config;

import com.duong.module.proxy.ecom.EcomAuthClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;


@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EcomOAuthProperties.class)
@EnableFeignClients(clients = EcomAuthClient.class)
public class EcomAuthConfiguration {
}
