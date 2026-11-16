package com.duong.module.proxy.payment.request;

import com.duong.module.proxy.customer.response.CustomerResponse;
import com.duong.module.proxy.payment.entity.enumeration.PaymentMethodEnum;

import java.math.BigDecimal;

public record PaymentRequest(
        BigDecimal amount,
        PaymentMethodEnum paymentMethod,
        Integer orderId,
        String orderReference,
        CustomerResponse customer
) {
}
