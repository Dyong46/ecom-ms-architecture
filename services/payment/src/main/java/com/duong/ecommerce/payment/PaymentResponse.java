package com.duong.ecommerce.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse (
        Integer Id,
        Integer orderId,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        LocalDateTime createDate,
        LocalDateTime lastModifiedDate
) {}
