package com.duong.ecommerce.payment.payload.request;

import com.duong.ecommerce.payment.entity.enumeration.PaymentMethodEnum;

import java.math.BigDecimal;

public record PaymentRequest(
        Integer id,
        BigDecimal amount,
        PaymentMethodEnum paymentMethod,
        Integer orderId,
        String orderReference,
        Customer customer
) {
}
