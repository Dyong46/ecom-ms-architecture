package com.duong.ecommerce.product.controller;

import com.duong.ecommerce.product.payload.request.*;
import com.duong.ecommerce.product.payload.response.*;
import com.duong.ecommerce.product.service.ProductService;
import com.duong.module.web.payload.filter.PaginationRequest;
import com.duong.module.web.payload.response.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/products") @RequiredArgsConstructor
public class ProductController {
    private final ProductService service;
    @PostMapping
    public BaseResponse<String> create(@RequestBody @Valid ProductRequest request) {
        return BaseResponse.of(service.createProduct(request));
    }
    @GetMapping("/{id}")
    public BaseResponse<ProductResponse> findById(@PathVariable String id) {
        return BaseResponse.of(service.findById(id));
    }
    @GetMapping
    public BaseResponse<PageResponse<ProductResponse>> findAll(@Valid @ModelAttribute PaginationRequest pagination,
            @RequestParam(required = false) String name) {
        return BaseResponse.of(PageResponse.build(name == null ? service.findAll(pagination.toPageable())
                : service.filter(new ProductFilter(name), pagination.toPageable())));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/variants")
    public BaseResponse<String> addVariant(@PathVariable String id, @RequestBody @Valid VariantRequest request) {
        return BaseResponse.of(service.addVariant(id, request));
    }
    @DeleteMapping("/{id}/variants/{skuId}")
    public ResponseEntity<Void> deleteVariant(@PathVariable String id, @PathVariable String skuId) {
        service.deleteVariant(id, skuId);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/purchase")
    public BaseResponse<List<ProductPurchaseResponse>> purchaseProduct(@RequestBody @Valid ProductPurchaseBatchRequest request) {
        return BaseResponse.of(service.purchaseProducts(request.products()));
    }
    private record ProductFilter(String nameLk) { }
}
