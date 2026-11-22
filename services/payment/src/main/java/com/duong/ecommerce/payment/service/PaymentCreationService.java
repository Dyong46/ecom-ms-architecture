package com.duong.ecommerce.payment.service;

import com.duong.ecommerce.payment.integration.idempotency.PostgresIdempotencyStore;
import com.duong.ecommerce.payment.payload.request.PaymentRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class PaymentCreationService {
    private final PaymentService payments;
    private final PostgresIdempotencyStore idempotency;

    private final com.duong.ecommerce.payment.config.StripeSettings stripe;
    private final PaymentAttemptService attempts;

    public Integer create(String key, @NotNull @Valid PaymentRequest request) {
        try {
            return idempotency.execute(key, request, new TypeReference<Integer>() {
                    },
                    operationId -> {
                        if(request.amount().signum()>0 && !stripe.enabled())
                            throw new com.duong.ecommerce.payment.exception.PaymentUnavailableException("Stripe sandbox is not configured");
                        return payments.createPayment(request);
                    });
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            throw new com.duong.module.common.exception.ConflictException("Payment conflicts with an existing record");
        }
    }
    public String retry(String key, int paymentId) {
        return idempotency.execute(key,java.util.Map.of("action","retry","paymentId",paymentId),new TypeReference<String>(){},
                operationId -> {
                    if(!stripe.enabled()) throw new com.duong.ecommerce.payment.exception.PaymentUnavailableException("Stripe sandbox is not configured");
                    return attempts.retry(paymentId);
                });
    }
}
