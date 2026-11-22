package com.duong.ecommerce.product.controller;
import com.duong.ecommerce.product.payload.request.*;
import com.duong.ecommerce.product.payload.response.*;
import com.duong.ecommerce.product.service.ProductOptionService;
import com.duong.module.web.payload.filter.PaginationRequest;
import com.duong.module.web.payload.response.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/product-options") @RequiredArgsConstructor
public class ProductOptionController {
    private final ProductOptionService service;
    @GetMapping
    public BaseResponse<PageResponse<OptionResponse>> findAll(@Valid @ModelAttribute PaginationRequest pagination) {
        return BaseResponse.of(PageResponse.build(service.findAll(pagination.toPageable())));
    }
    @PostMapping
    public BaseResponse<String> create(@RequestBody @Valid OptionRequest request) {
        return BaseResponse.of(service.create(request));
    }
    @PostMapping("/{id}/values")
    public BaseResponse<String> addValue(@PathVariable String id, @RequestBody @Valid OptionValueRequest request) {
        return BaseResponse.of(service.addValue(id, request));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/{id}/values/{valueId}")
    public ResponseEntity<Void> deleteValue(@PathVariable String id, @PathVariable String valueId) {
        service.deleteValue(id, valueId);
        return ResponseEntity.noContent().build();
    }
}
