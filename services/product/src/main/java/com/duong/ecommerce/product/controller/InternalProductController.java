package com.duong.ecommerce.product.controller;

import com.duong.ecommerce.product.payload.request.ProductPurchaseBatchRequest;
import com.duong.ecommerce.product.payload.response.ProductPurchaseResponse;
import com.duong.ecommerce.product.service.ProductPurchaseService;
import com.duong.module.web.payload.response.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/products")
@RequiredArgsConstructor
public class InternalProductController {
    private final ProductPurchaseService service;

    @PostMapping("/purchase")
    public BaseResponse<List<ProductPurchaseResponse>> purchase(@RequestHeader("Idempotency-Key") String key,
                                                                @RequestBody @Valid ProductPurchaseBatchRequest request) {
        return BaseResponse.of(service.purchase(key, request.products()));
    }
}
