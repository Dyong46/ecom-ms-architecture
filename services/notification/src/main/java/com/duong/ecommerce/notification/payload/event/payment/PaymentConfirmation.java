package com.duong.ecommerce.notification.payload.event.payment;

import com.duong.ecommerce.notification.enumtype.PaymentMethod;

import java.math.BigDecimal;

@org.springframework.data.annotation.TypeAlias("com.duong.ecommerce.kafka.payment.PaymentConfirmation")
public record PaymentConfirmation(
        String orderReference,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String customerFirstname,
        String customerLastname,
        String customerEmail,
        String eventId
) {
}
