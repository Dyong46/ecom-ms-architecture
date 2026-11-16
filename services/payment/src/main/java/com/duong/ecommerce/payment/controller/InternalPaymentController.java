package com.duong.ecommerce.payment.controller;

import com.duong.ecommerce.payment.payload.request.PaymentRequest;
import com.duong.ecommerce.payment.service.PaymentService;

import com.duong.module.web.payload.response.BaseResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class InternalPaymentController {
    private final PaymentService service;

    @PostMapping
    public BaseResponse<Integer> createPayment(@RequestBody @Valid PaymentRequest request) {
        return BaseResponse.of(service.createPayment(request));
    }
}
