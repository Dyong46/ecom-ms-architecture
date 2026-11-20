package com.duong.ecommerce.payment.controller;

import com.duong.ecommerce.payment.payload.request.PaymentRequest;
import com.duong.ecommerce.payment.payload.response.PaymentResponse;
import com.duong.ecommerce.payment.service.PaymentService;
import com.duong.module.web.payload.filter.PaginationRequest;
import com.duong.module.web.payload.response.BaseResponse;
import com.duong.module.web.payload.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService service;
    private final com.duong.ecommerce.payment.service.PaymentCreationService creation;

    @PostMapping
    public ResponseEntity<BaseResponse<Integer>> createPayment(
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody @Valid PaymentRequest request
    ) {
        return ResponseEntity.ok(BaseResponse.of(creation.create(key, request)));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<PaymentResponse>>> getPayment(
            @Valid @ModelAttribute PaginationRequest pagination
    ) {
        return ResponseEntity.ok(BaseResponse.of(PageResponse.build(service.findAll(pagination.toPageable()))));
    }
}
