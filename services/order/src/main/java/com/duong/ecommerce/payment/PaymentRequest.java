package com.duong.ecommerce.payment;

import com.duong.ecommerce.customer.CustomerResponse;
import com.duong.ecommerce.order.PaymentMethod;

import java.math.BigDecimal;

public record PaymentRequest(
        BigDecimal amount,
        PaymentMethod paymentMethod,
        Integer orderId,
        String orderReference,
        CustomerResponse customer
) {
}
