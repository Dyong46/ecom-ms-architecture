package com.duong.ecommerce.notification.integration.kafka.consumer;

import com.duong.ecommerce.notification.mapper.NotificationMapper;
import com.duong.ecommerce.notification.service.EmailService;
import com.duong.ecommerce.notification.payload.event.order.OrderConfirmation;
import com.duong.ecommerce.notification.payload.event.payment.PaymentConfirmation;
import com.duong.ecommerce.notification.entity.enumeration.DeliveryStatusEnum;
import com.duong.ecommerce.notification.entity.Notification;
import com.duong.ecommerce.notification.entity.enumeration.NotificationTypeEnum;
import com.duong.ecommerce.notification.repository.EmailJobStore;
import com.duong.module.mail.message.MailMessage;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationConsumer {
    private final EmailJobStore jobs;
    private final EmailService renderer;
    private final Clock clock;
    private final NotificationMapper mapper;

    @KafkaListener(topics = "${application.events.payment-topic:payment-topic}")
    public void consumePaymentSuccessNotification(ConsumerRecord<String, PaymentConfirmation> record, Acknowledgment acknowledgment) {
        var event = record.value();
        var job = newJob(event.eventId(), record, NotificationTypeEnum.PAYMENT_CONFIRMATION);
        job.setPaymentConfirmation(event);
        prepare(job, () -> renderer.buildPaymentSuccessEmail(event.customerEmail(),
                event.customerFirstname() + " " + event.customerLastname(), event.amount(), event.orderReference(), event.currencyCode()));
        jobs.enqueue(job);
        acknowledgment.acknowledge();
    }

    @KafkaListener(topics = "${application.events.order-topic:order-topic}")
    public void consumeOrderConfirmationNotification(ConsumerRecord<String, OrderConfirmation> record, Acknowledgment acknowledgment) {
        var event = record.value();
        var job = newJob(event.eventId(), record, NotificationTypeEnum.ORDER_CONFIRMATION);
        job.setOrderConfirmation(event);
        prepare(job, () -> renderer.buildOrderConfirmationEmail(event.customer().email(),
                event.customer().firstname() + " " + event.customer().lastname(), event.totalAmount(),
                event.orderReference(), event.products()));
        jobs.enqueue(job);
        acknowledgment.acknowledge();
    }

    private Notification newJob(String eventId, ConsumerRecord<?, ?> record, NotificationTypeEnum type) {
        String id = eventId == null || eventId.isBlank()
                ? "legacy:" + record.topic() + ":" + record.partition() + ":" + record.offset() : eventId;
        return Notification.builder().id(id).eventId(id).type(type).status(DeliveryStatusEnum.PENDING)
                .nextRetryAt(clock.instant()).notificationDate(LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault())).build();
    }

    private void prepare(Notification job, java.util.function.Supplier<MailMessage> build) {
        try {
            var mail = build.get();
            mapper.updateMailSnapshot(mail, job);
        } catch (RuntimeException exception) {
            job.setStatus(DeliveryStatusEnum.FAILED);
            job.setLastError("MESSAGE_PREPARATION_FAILED");
            job.setNextRetryAt(null);
        }
    }
}
