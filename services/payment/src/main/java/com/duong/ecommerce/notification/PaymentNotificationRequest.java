package com.duong.ecommerce.notification;

import com.duong.ecommerce.payment.PaymentMethod;

import java.math.BigDecimal;

public record PaymentNotificationRequest(
        String orderReference,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String customerFirstname,
        String customerLastname,
        String customerEmail,
        String eventId
) {
    public PaymentNotificationRequest(
        String orderReference,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String customerFirstname,
        String customerLastname,
        String customerEmail
    ) {
        this(orderReference, amount, paymentMethod, customerFirstname, customerLastname, customerEmail, java.util.UUID.randomUUID().toString());
    }
}
