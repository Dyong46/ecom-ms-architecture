package com.duong.module.proxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("proxy.http")
public record ProxyHttpProperties(
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout) {
    public ProxyHttpProperties {
        validateTimeout(connectTimeout);
        validateTimeout(readTimeout);
    }

    private static void validateTimeout(Duration value) {
        if (value == null || value.toMillis() < 1 || value.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Proxy timeouts must be between 1ms and 2147483647ms");
        }
    }
}
