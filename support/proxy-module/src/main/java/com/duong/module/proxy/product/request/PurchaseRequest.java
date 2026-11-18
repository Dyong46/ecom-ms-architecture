package com.duong.module.proxy.product.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
public record PurchaseRequest(
        @NotBlank(message = "SKU is mandatory")
        @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String skuId,
        @Positive(message = "Quantity must be positive") int quantity) { }
