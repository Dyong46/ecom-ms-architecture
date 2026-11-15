package com.duong.ecommerce.notification;

import com.duong.module.kafka.service.KafkaPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;

@Service
public class NotificationProducer {
    private final KafkaPublisher publisher;
    private final String topic;

    public NotificationProducer(KafkaPublisher publisher, @Value("${application.events.payment-topic:payment-topic}") String topic) {
        this.publisher = publisher;
        this.topic = topic;
    }

    public CompletableFuture<SendResult<Object, Object>> sendNotification(PaymentNotificationRequest event) {
        return publisher.send(topic, event.orderReference(), event);
    }
}
