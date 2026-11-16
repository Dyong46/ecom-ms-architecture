package com.duong.ecommerce.notification.payload.event.payment;

import com.duong.ecommerce.notification.entity.enumeration.PaymentMethodEnum;

import java.math.BigDecimal;

@org.springframework.data.annotation.TypeAlias("com.duong.ecommerce.kafka.payment.PaymentConfirmation")
public record PaymentConfirmation(
        String orderReference,
        BigDecimal amount,
        PaymentMethodEnum paymentMethod,
        String customerFirstname,
        String customerLastname,
        String customerEmail,
        String eventId
) {
}
