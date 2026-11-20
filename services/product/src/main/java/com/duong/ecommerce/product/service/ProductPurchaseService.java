package com.duong.ecommerce.product.service;

import com.duong.ecommerce.product.integration.idempotency.PostgresIdempotencyStore;
import com.duong.ecommerce.product.payload.request.ProductPurchaseRequest;
import com.duong.ecommerce.product.payload.response.ProductPurchaseResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Comparator;
import java.util.List;

@Service
@Validated
@RequiredArgsConstructor
public class ProductPurchaseService {
    private final ProductService products;
    private final PostgresIdempotencyStore idempotency;

    public List<ProductPurchaseResponse> purchase(String key,
                                                  @NotEmpty List<@NotNull @Valid ProductPurchaseRequest> request) {
        var canonical = request.stream().sorted(Comparator.comparing(ProductPurchaseRequest::skuId)).toList();
        return idempotency.execute(key, canonical, new TypeReference<List<ProductPurchaseResponse>>() {
                },
                operationId -> products.purchaseProducts(request));
    }
}
