package com.duong.ecommerce.product;
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
    public ResponseEntity<Integer> create(
            @RequestBody @Valid ProductRequest request
    ) {
        return ResponseEntity.ok(service.createProduct(request));
    }

    @PostMapping("/purchase")
    public  ResponseEntity<List<ProductPurchaseResponse>> purchaseProduct(
            @RequestBody @Valid ProductPurchaseBatchRequest request
    ) {
        return ResponseEntity.ok(service.purchaseProducts(request.products()));
    }

    @GetMapping("/{product-id}")
    public ResponseEntity<Product> findById(
            @PathVariable("product-id") Integer productId
    ) {
        return ResponseEntity.ok(service.findById(productId));
    }

    @GetMapping
    public ResponseEntity<PageResponse<Product>> findAll(
            @Valid @ModelAttribute PaginationRequest pagination,
            @RequestParam(required = false) String name
    ) {
        return ResponseEntity.ok(PageResponse.build(name == null ? service.findAll(pagination.toPageable())
                : service.filter(new ProductFilter(name), pagination.toPageable())));
    }

    private record ProductFilter(String nameLk) { }

}
