package com.duong.ecommerce.payment.payload.event;

import com.duong.ecommerce.payment.entity.enumeration.PaymentMethodEnum;

import java.math.BigDecimal;

public record PaymentNotificationRequest(
        String orderReference,
        BigDecimal amount,
        PaymentMethodEnum paymentMethod,
        String customerFirstname,
        String customerLastname,
        String customerEmail,
        String eventId
) {
    public PaymentNotificationRequest(
        String orderReference,
        BigDecimal amount,
        PaymentMethodEnum paymentMethod,
        String customerFirstname,
        String customerLastname,
        String customerEmail
    ) {
        this(orderReference, amount, paymentMethod, customerFirstname, customerLastname, customerEmail, java.util.UUID.randomUUID().toString());
    }
}
