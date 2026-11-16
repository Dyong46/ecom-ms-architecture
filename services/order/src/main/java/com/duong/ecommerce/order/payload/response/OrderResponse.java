package com.duong.ecommerce.order.payload.response;

import com.duong.ecommerce.order.enumtype.PaymentMethod;

import java.math.BigDecimal;

public record OrderResponse(
        Integer id,
        String reference,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String customerId
) {
}
