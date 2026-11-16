package com.duong.ecommerce.product.payload.request;

import com.duong.ecommerce.product.entity.Product;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProductPurchaseRequest(
        @NotNull(message = "Product is mandatory")
        Integer productId,

        @Positive(message = "Quantity is mandatory")
        double quantity
) {
}
