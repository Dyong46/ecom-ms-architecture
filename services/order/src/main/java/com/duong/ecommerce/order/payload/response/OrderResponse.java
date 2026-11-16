package com.duong.ecommerce.order.payload.response;

import com.duong.ecommerce.order.entity.enumeration.PaymentMethodEnum;

import java.math.BigDecimal;

public record OrderResponse(
        Integer id,
        String reference,
        BigDecimal amount,
        PaymentMethodEnum paymentMethod,
        String customerId
) {
}
