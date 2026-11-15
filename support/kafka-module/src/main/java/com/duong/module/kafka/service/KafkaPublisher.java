package com.duong.module.kafka.service;

import com.duong.module.kafka.config.KafkaModuleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Publishes events and exposes broker delivery completion. */
public final class KafkaPublisher {
    private static final Logger log = LoggerFactory.getLogger(KafkaPublisher.class);
    private final KafkaTemplate<Object, Object> template;

    public KafkaPublisher(KafkaTemplate<Object, Object> template) {
        this.template = template;
    }

    public CompletableFuture<SendResult<Object, Object>> send(String topic, Object event) {
        return send(topic, null, event);
    }

    public CompletableFuture<SendResult<Object, Object>> send(String topic, String key, Object event) {
        KafkaModuleProperties.requireTopic(topic);
        Objects.requireNonNull(event, "Kafka event must not be null");
        try {
            return template.send(topic, key, event);
        } catch (RuntimeException ex) {
            // Serialization errors can occur before KafkaTemplate returns a future.
            log.error("Kafka publish failed before enqueue topic={} errorType={}", topic, ex.getClass().getSimpleName());
            return CompletableFuture.failedFuture(ex);
        }
    }
}
