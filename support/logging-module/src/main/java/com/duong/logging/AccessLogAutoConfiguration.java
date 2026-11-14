package com.duong.logging;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
@ConditionalOnClass(ObservationRegistry.class)
@ConditionalOnProperty(prefix = "observability.access-log", name = "enabled", matchIfMissing = true)
public class AccessLogAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
    static class ServletConfiguration {
        @Bean
        ServletAccessLogHandler servletAccessLogHandler(
                @Value("${management.endpoints.web.base-path:/actuator}") String actuatorPath) {
            return new ServletAccessLogHandler(actuatorPath);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    static class ReactiveConfiguration {
        @Bean
        ReactiveAccessLogHandler reactiveAccessLogHandler(
                @Value("${management.endpoints.web.base-path:/actuator}") String actuatorPath) {
            return new ReactiveAccessLogHandler(actuatorPath);
        }
    }
}
