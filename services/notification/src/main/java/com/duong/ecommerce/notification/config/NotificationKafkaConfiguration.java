package com.duong.ecommerce.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class NotificationKafkaConfiguration {
    @Bean
    DefaultErrorHandler notificationErrorHandler() {
        var handler = new DefaultErrorHandler(ingestionBackOff());
        handler.setClassifications(java.util.Map.of(), true);
        // A different Mongo/network exception must not restart a failing record's delay.
        handler.setResetStateOnExceptionChange(false);
        handler.setAckAfterHandle(false);
        handler.setCommitRecovered(false);
        handler.setLogLevel(KafkaException.Level.DEBUG);
        handler.setRetryListeners((record, exception, attempt) ->
                log.warn("Kafka ingestion failed topic={} partition={} offset={} attempt={} cause={}; retry pending",
                        record.topic(), record.partition(), record.offset(), attempt,
                        NestedExceptionUtils.getMostSpecificCause(exception).getClass().getSimpleName()));
        return handler;
    }

    static ExponentialBackOff ingestionBackOff() {
        var backOff = new ExponentialBackOff(1000, 2.0);
        backOff.setMaxInterval(60_000);
        return backOff;
    }
}
