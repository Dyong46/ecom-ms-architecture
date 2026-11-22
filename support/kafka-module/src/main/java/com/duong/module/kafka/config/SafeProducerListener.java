package com.duong.module.kafka.config;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.ProducerListener;

/** Avoid LoggingProducerListener logging customer payloads or keys on asynchronous failures. */
public final class SafeProducerListener implements ProducerListener<Object, Object> {
    private static final Logger log = LoggerFactory.getLogger(SafeProducerListener.class);

    @Override
    public void onError(ProducerRecord<Object, Object> record, RecordMetadata metadata, Exception exception) {
        log.error("Kafka publish failed topic={} partition={} errorType={}",
                record.topic(), record.partition(), exception.getClass().getSimpleName());
    }
}
