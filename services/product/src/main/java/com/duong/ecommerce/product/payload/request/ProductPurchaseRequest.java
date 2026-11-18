package com.duong.ecommerce.product.payload.request;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.UUID;
public record ProductPurchaseRequest(
        @NotBlank(message = "SKU is mandatory") @UUID String skuId,
        @Positive(message = "Quantity must be positive") int quantity) { }
