package com.duong.module.proxy.payment.request;

import com.duong.module.proxy.customer.response.CustomerResponse;
import com.duong.module.proxy.payment.enumtype.PaymentMethod;

import java.math.BigDecimal;

public record PaymentRequest(
        BigDecimal amount,
        PaymentMethod paymentMethod,
        Integer orderId,
        String orderReference,
        CustomerResponse customer
) {
}
