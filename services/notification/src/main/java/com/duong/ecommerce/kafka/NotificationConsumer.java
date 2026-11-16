package com.duong.ecommerce.kafka;

import com.duong.ecommerce.email.EmailService;
import com.duong.ecommerce.kafka.order.OrderConfirmation;
import com.duong.ecommerce.kafka.payment.PaymentConfirmation;
import com.duong.ecommerce.notification.DeliveryStatus;
import com.duong.ecommerce.notification.Notification;
import com.duong.ecommerce.notification.NotificationType;
import com.duong.ecommerce.notification.delivery.EmailJobStore;
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

    @KafkaListener(topics = "${application.events.payment-topic:payment-topic}")
    public void consumePaymentSuccessNotification(ConsumerRecord<String, PaymentConfirmation> record, Acknowledgment acknowledgment) {
        var event = record.value();
        var job = newJob(event.eventId(), record, NotificationType.PAYMENT_CONFIRMATION);
        job.setPaymentConfirmation(event);
        prepare(job, () -> renderer.buildPaymentSuccessEmail(event.customerEmail(),
                event.customerFirstname() + " " + event.customerLastname(), event.amount(), event.orderReference()));
        jobs.enqueue(job);
        acknowledgment.acknowledge();
    }

    @KafkaListener(topics = "${application.events.order-topic:order-topic}")
    public void consumeOrderConfirmationNotification(ConsumerRecord<String, OrderConfirmation> record, Acknowledgment acknowledgment) {
        var event = record.value();
        var job = newJob(event.eventId(), record, NotificationType.ORDER_CONFIRMATION);
        job.setOrderConfirmation(event);
        prepare(job, () -> renderer.buildOrderConfirmationEmail(event.customer().email(),
                event.customer().firstname() + " " + event.customer().lastname(), event.totalAmount(),
                event.orderReference(), event.products()));
        jobs.enqueue(job);
        acknowledgment.acknowledge();
    }

    private Notification newJob(String eventId, ConsumerRecord<?, ?> record, NotificationType type) {
        String id = eventId == null || eventId.isBlank()
                ? "legacy:" + record.topic() + ":" + record.partition() + ":" + record.offset() : eventId;
        return Notification.builder().id(id).eventId(id).type(type).status(DeliveryStatus.PENDING)
                .nextRetryAt(clock.instant()).notificationDate(LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault())).build();
    }

    private void prepare(Notification job, java.util.function.Supplier<MailMessage> build) {
        try {
            var mail = build.get();
            job.setSenderAddress(mail.senderAddress()); job.setRecipient(mail.receivers().get(0));
            job.setSubject(mail.subject()); job.setContent(mail.content()); job.setHtml(mail.html());
        } catch (RuntimeException exception) {
            job.setStatus(DeliveryStatus.FAILED);
            job.setLastError("MESSAGE_PREPARATION_FAILED");
            job.setNextRetryAt(null);
        }
    }
}
