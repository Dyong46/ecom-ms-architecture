package com.duong.ecommerce.product.controller;

import com.duong.module.web.payload.response.BaseResponse;

import com.duong.ecommerce.product.entity.Product;
import com.duong.ecommerce.product.service.ProductService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductEntityController {
    private final ProductService service;

    @GetMapping("/{id}/entity")
    public BaseResponse<Product> findEntityById(@PathVariable Integer id) {
        return BaseResponse.of(service.findById(id));
    }

}
