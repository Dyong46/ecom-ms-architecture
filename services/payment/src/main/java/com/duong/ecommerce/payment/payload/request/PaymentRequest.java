package com.duong.ecommerce.payment.payload.request;

import com.duong.ecommerce.payment.entity.enumeration.PaymentMethodEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 36, fraction = 2) BigDecimal amount,
        @NotNull PaymentMethodEnum paymentMethod,
        @NotNull @Positive Integer orderId,
        @Size(max = 255) String orderReference,
        @NotNull @Valid Customer customer
) {
}
