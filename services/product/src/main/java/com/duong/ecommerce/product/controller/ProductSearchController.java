package com.duong.ecommerce.product.controller;

import com.duong.ecommerce.product.integration.search.SkuSearchDocument;
import com.duong.ecommerce.product.payload.request.ProductSearchRequest;
import com.duong.ecommerce.product.service.ProductSearchService;
import com.duong.module.web.payload.filter.PaginationRequest;
import com.duong.module.web.payload.response.BaseResponse;
import com.duong.module.web.payload.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products/search")
@RequiredArgsConstructor
public class ProductSearchController {
    private final ProductSearchService service;

    @GetMapping
    public BaseResponse<PageResponse<SkuSearchDocument>> search(
            @Valid @ModelAttribute ProductSearchRequest request,
            @Valid @ModelAttribute PaginationRequest pagination) {
        return BaseResponse.of(PageResponse.build(service.search(request, pagination.toPageable())));
    }

    @PostMapping("/reindex")
    public BaseResponse<Long> reindex() {
        return BaseResponse.of(service.reindex());
    }
}
