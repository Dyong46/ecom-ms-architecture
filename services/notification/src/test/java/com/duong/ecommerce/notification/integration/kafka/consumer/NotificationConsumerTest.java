package com.duong.ecommerce.notification.integration.kafka.consumer;

import com.duong.ecommerce.notification.entity.Notification;
import com.duong.ecommerce.notification.entity.enumeration.DeliveryStatusEnum;
import com.duong.ecommerce.notification.entity.enumeration.PaymentMethodEnum;
import com.duong.ecommerce.notification.payload.event.payment.PaymentConfirmation;

import com.duong.ecommerce.notification.service.EmailService;
import com.duong.ecommerce.notification.repository.EmailJobStore;
import com.duong.module.mail.message.MailMessage;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationConsumerTest {
    final EmailJobStore store = mock(EmailJobStore.class);
    final EmailService renderer = mock(EmailService.class);
    final Acknowledgment ack = mock(Acknowledgment.class);
    final NotificationConsumer consumer = new NotificationConsumer(store,renderer,
            Clock.fixed(Instant.parse("2026-11-16T00:00:00Z"),ZoneOffset.UTC),
            new com.duong.ecommerce.notification.mapper.NotificationMapperImpl());
    ConsumerRecord<String,PaymentConfirmation> event(String id) {
        return new ConsumerRecord<>("payment-topic",2,17,"ORDER-1",new PaymentConfirmation("ORDER-1",BigDecimal.TEN,
                PaymentMethodEnum.PAYPAL,"Ada","Lovelace","ada@example.test",id));
    }
    void prepare() {
        when(renderer.buildPaymentSuccessEmail(any(),any(),any(),any(),any())).thenReturn(
                new MailMessage("shop@example.test",List.of("ada@example.test"),"Subject","Snapshot",true));
    }
    @Test void persistenceMustPrecedeAck() {
        prepare(); consumer.consumePaymentSuccessNotification(event("event-1"),ack);
        var job=ArgumentCaptor.forClass(Notification.class);
        var order=inOrder(store,ack); order.verify(store).enqueue(job.capture()); order.verify(ack).acknowledge();
        assertThat(job.getValue().getId()).isEqualTo("event-1");
        assertThat(job.getValue().getStatus()).isEqualTo(DeliveryStatusEnum.PENDING);
        assertThat(job.getValue().getContent()).isEqualTo("Snapshot");
        assertThat(job.getValue().getNotificationDate()).isEqualTo(LocalDateTime.ofInstant(
                Instant.parse("2026-11-16T00:00:00Z"), ZoneId.systemDefault()));
    }
    @Test void storageFailureDoesNotAck() {
        prepare(); doThrow(new org.springframework.dao.DataAccessResourceFailureException("Mongo down")).when(store).enqueue(any());
        assertThatThrownBy(() -> consumer.consumePaymentSuccessNotification(event("event-1"),ack))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        verifyNoInteractions(ack);
    }
    @Test void legacyIdentitySurvivesRedelivery() {
        prepare(); consumer.consumePaymentSuccessNotification(event(null),ack);
        consumer.consumePaymentSuccessNotification(event(null),ack);
        var jobs=ArgumentCaptor.forClass(Notification.class); verify(store,times(2)).enqueue(jobs.capture());
        assertThat(jobs.getAllValues()).extracting(Notification::getId)
                .containsExactly("legacy:payment-topic:2:17","legacy:payment-topic:2:17");
    }
    @Test void renderingFailureIsPersistedBeforeAck() {
        when(renderer.buildPaymentSuccessEmail(any(),any(),any(),any(),any())).thenThrow(new IllegalArgumentException("Bad data"));
        consumer.consumePaymentSuccessNotification(event("event-1"),ack);
        var job=ArgumentCaptor.forClass(Notification.class); verify(store).enqueue(job.capture());
        assertThat(job.getValue().getStatus()).isEqualTo(DeliveryStatusEnum.FAILED);
        assertThat(job.getValue().getAttemptCount()).isZero();
        assertThat(job.getValue().getLastError()).isEqualTo("MESSAGE_PREPARATION_FAILED");
        verify(ack).acknowledge();
    }
}
