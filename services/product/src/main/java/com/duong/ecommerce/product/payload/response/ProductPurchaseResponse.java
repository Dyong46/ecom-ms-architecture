package com.duong.ecommerce.product.payload.response;
import java.math.BigDecimal;
import java.util.Map;
public record ProductPurchaseResponse(String productId, String skuId, String sku,
        String name, String description, Map<String, String> options, BigDecimal price, int quantity) { }
