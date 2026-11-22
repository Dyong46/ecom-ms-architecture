package com.duong.ecommerce.payment.entity;

import java.time.Instant;

/** Durable attempt snapshot, mapped by JDBC; state transitions belong to Payment. */
public record PaymentAttempt(String id, Integer paymentId, long amount, String currencyCode, String status,
        String providerRequestKey, String requestJson, String sessionId, Instant createdAt,
        String leaseToken, int retries) { }
