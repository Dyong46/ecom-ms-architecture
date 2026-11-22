package com.duong.ecommerce.payment.payload.response;

import com.duong.ecommerce.payment.entity.enumeration.PaymentMethodEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse (
        Integer Id,
        Integer orderId,
        BigDecimal amount,
        PaymentMethodEnum paymentMethod,
        LocalDateTime createDate,
        LocalDateTime lastModifiedDate
) {}
