package com.duong.module.proxy.product.response;
import java.math.BigDecimal;
import java.util.Map;
public record PurchaseResponse(String productId, String skuId, String sku,
        String name, String description, Map<String, String> options, BigDecimal price, int quantity) { }
