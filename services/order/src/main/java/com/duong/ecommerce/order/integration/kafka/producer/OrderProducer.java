package com.duong.ecommerce.order.integration.kafka.producer;

import com.duong.ecommerce.order.payload.event.OrderConfirmation;

import com.duong.module.kafka.service.KafkaPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;

@Service
public class OrderProducer {
    private final KafkaPublisher publisher;
    private final String topic;

    public OrderProducer(KafkaPublisher publisher, @Value("${application.events.order-topic:order-topic}") String topic) {
        this.publisher = publisher;
        this.topic = topic;
    }

    public CompletableFuture<SendResult<Object, Object>> sendOrderConfirmation(OrderConfirmation event) {
        return publisher.send(topic, event.orderReference(), event);
    }
}
