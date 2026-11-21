package com.duong.ecommerce.payment.controller;

import com.duong.ecommerce.payment.payload.request.PaymentRequest;
import com.duong.ecommerce.payment.payload.response.PaymentResponse;
import com.duong.ecommerce.payment.service.PaymentCreationService;
import com.duong.ecommerce.payment.service.PaymentService;
import com.duong.module.web.payload.filter.PaginationRequest;
import com.duong.module.web.payload.response.BaseResponse;
import com.duong.module.web.payload.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class InternalPaymentController {
    private final PaymentCreationService service;
    private final PaymentService payments;

    @PostMapping
    public BaseResponse<Integer> createPayment(@RequestHeader("Idempotency-Key") String key,
                                               @RequestBody @Valid PaymentRequest request) {
        return BaseResponse.of(service.create(key, request));
    }

    @GetMapping
    public BaseResponse<PageResponse<PaymentResponse>> findAll(
            @Valid @ModelAttribute PaginationRequest pagination) {
        return BaseResponse.of(PageResponse.build(payments.findAll(pagination.toPageable())));
    }
}
