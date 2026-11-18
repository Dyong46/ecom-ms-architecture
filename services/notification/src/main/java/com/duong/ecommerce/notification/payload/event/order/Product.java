package com.duong.ecommerce.notification.payload.event.order;
import java.math.BigDecimal;
import java.util.Map;
@org.springframework.data.annotation.TypeAlias("com.duong.ecommerce.kafka.order.Product")
public record Product(String productId, String skuId, String sku,
        String name, String description, Map<String, String> options, BigDecimal price, int quantity) { }
