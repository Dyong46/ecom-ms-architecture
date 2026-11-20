package com.duong.ecommerce.payment.controller;

import com.duong.ecommerce.payment.payload.request.PaymentRequest;
import com.duong.ecommerce.payment.service.PaymentCreationService;
import com.duong.module.web.payload.response.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class InternalPaymentController {
    private final PaymentCreationService service;

    @PostMapping
    public BaseResponse<Integer> createPayment(@RequestHeader("Idempotency-Key") String key,
                                               @RequestBody @Valid PaymentRequest request) {
        return BaseResponse.of(service.create(key, request));
    }
}
