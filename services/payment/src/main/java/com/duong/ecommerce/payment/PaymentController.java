package com.duong.ecommerce.payment;
import com.duong.module.web.payload.response.PageResponse;
import com.duong.module.web.payload.filter.PaginationRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService service;

    @PostMapping
    public ResponseEntity<Integer> createPayment(
            @RequestBody @Valid PaymentRequest request
    ) {
        return ResponseEntity.ok(service.createPayment(request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<PaymentResponse>> getPayment(
            @Valid @ModelAttribute PaginationRequest pagination
    ) {
        return ResponseEntity.ok(PageResponse.build(service.findAll(pagination.toPageable())));
    }
}
