package com.duong.ecommerce.order.orderline.payload.request;
import java.math.BigDecimal;
import java.util.Map;
public record OrderLineRequest(Integer id, Integer orderId, String productId, String skuId,
        String sku, String name, Map<String, String> options, BigDecimal unitPrice, int quantity) { }
