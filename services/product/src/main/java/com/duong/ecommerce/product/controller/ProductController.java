package com.duong.ecommerce.product.controller;

import com.duong.module.web.payload.response.BaseResponse;

import com.duong.ecommerce.product.entity.Product;
import com.duong.ecommerce.product.payload.request.ProductPurchaseBatchRequest;
import com.duong.ecommerce.product.payload.response.ProductPurchaseResponse;
import com.duong.ecommerce.product.payload.request.ProductRequest;
import com.duong.ecommerce.product.service.ProductService;
import com.duong.module.web.payload.response.PageResponse;
import com.duong.module.web.payload.filter.PaginationRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService service;

    @DeleteMapping("/{product-id}")
    public ResponseEntity<Void> delete(@PathVariable("product-id") Integer productId) {
        service.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<BaseResponse<Integer>> create(
            @RequestBody @Valid ProductRequest request
    ) {
        return ResponseEntity.ok(BaseResponse.of(service.createProduct(request)));
    }

    @PostMapping("/purchase")
    public  ResponseEntity<BaseResponse<List<ProductPurchaseResponse>>> purchaseProduct(
            @RequestBody @Valid ProductPurchaseBatchRequest request
    ) {
        return ResponseEntity.ok(BaseResponse.of(service.purchaseProducts(request.products())));
    }

    @GetMapping("/{product-id}")
    public ResponseEntity<BaseResponse<Product>> findById(
            @PathVariable("product-id") Integer productId
    ) {
        return ResponseEntity.ok(BaseResponse.of(service.findById(productId)));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<Product>>> findAll(
            @Valid @ModelAttribute PaginationRequest pagination,
            @RequestParam(required = false) String name
    ) {
        return ResponseEntity.ok(BaseResponse.of(PageResponse.build(name == null ? service.findAll(pagination.toPageable())
                : service.filter(new ProductFilter(name), pagination.toPageable()))));
    }

    private record ProductFilter(String nameLk) { }

}
