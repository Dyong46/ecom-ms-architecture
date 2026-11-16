package com.duong.ecommerce.product.controller;

import com.duong.module.web.payload.response.BaseResponse;

import com.duong.ecommerce.product.dto.ProductPurchaseBatchRequest;
import com.duong.ecommerce.product.dto.ProductPurchaseResponse;
import com.duong.ecommerce.product.service.ProductService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/products")
@RequiredArgsConstructor
public class InternalProductController {
    private final ProductService service;

    @PostMapping("/purchase")
    public BaseResponse<List<ProductPurchaseResponse>> purchase(@RequestBody @Valid ProductPurchaseBatchRequest request) {
        return BaseResponse.of(service.purchaseProducts(request.products()));
    }
}
