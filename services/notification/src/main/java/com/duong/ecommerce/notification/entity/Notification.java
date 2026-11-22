package com.duong.ecommerce.notification.entity;

import com.duong.ecommerce.notification.entity.enumeration.DeliveryStatusEnum;
import com.duong.ecommerce.notification.entity.enumeration.NotificationTypeEnum;

import com.duong.ecommerce.notification.payload.event.order.OrderConfirmation;
import com.duong.ecommerce.notification.payload.event.payment.PaymentConfirmation;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@Document
@org.springframework.data.annotation.TypeAlias("com.duong.ecommerce.notification.Notification")
public class Notification {

    @Id
    private String id;

    // New jobs use eventId as Mongo _id, enforcing uniqueness without a secondary index.
    private String eventId;
    private NotificationTypeEnum type;
    private DeliveryStatusEnum status;
    private int attemptCount;
    private java.time.Instant nextRetryAt;
    private java.time.Instant leaseUntil;
    private String leaseToken;
    private java.time.Instant submittedAt;
    private java.time.Instant lastAttemptAt;
    private String lastError;
    private String senderAddress;
    private String recipient;
    private String subject;
    private String content;
    private boolean html;

    private LocalDateTime notificationDate;

    private OrderConfirmation orderConfirmation;

    private PaymentConfirmation paymentConfirmation;

}
