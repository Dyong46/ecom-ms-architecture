package com.duong.ecommerce.order.controller;

import com.duong.ecommerce.order.payload.request.OrderRequest;
import com.duong.ecommerce.order.payload.response.OrderResponse;
import com.duong.ecommerce.order.service.OrderService;
import com.duong.module.web.payload.filter.PaginationRequest;
import com.duong.module.web.payload.response.BaseResponse;
import com.duong.module.web.payload.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService service;
    private final com.duong.ecommerce.order.service.OrderCheckoutService checkout;

    @PostMapping
    public ResponseEntity<BaseResponse<Integer>> createOrder(
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody @Valid OrderRequest request
    ) {
        return ResponseEntity.ok(BaseResponse.of(checkout.create(key, request)));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<OrderResponse>>> findAll(
            @Valid @ModelAttribute PaginationRequest pagination
    ) {
        return ResponseEntity.ok(BaseResponse.of(PageResponse.build(service.findAll(pagination.toPageable()))));
    }

    @GetMapping("/{order-id}")
    public ResponseEntity<BaseResponse<OrderResponse>> findById(
            @PathVariable("order-id") Integer orderId
    ) {
        return ResponseEntity.ok(BaseResponse.of(service.findById(orderId)));
    }
}
