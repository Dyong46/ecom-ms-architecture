package com.duong.module.proxy.payment.response;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentCheckoutResponse(Integer paymentId, Integer orderId, BigDecimal amount, String currencyCode,
        String status, String attemptId, String attemptStatus, String checkoutUrl, Instant expiresAt,
        String failureCode) { }
