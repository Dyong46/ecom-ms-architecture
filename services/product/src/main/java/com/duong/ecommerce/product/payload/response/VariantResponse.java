package com.duong.ecommerce.product.payload.response;
import java.math.BigDecimal;
import java.util.List;
public record VariantResponse(String id, String sku, BigDecimal price, int stockQuantity,
        List<VariantOptionResponse> options) { }
