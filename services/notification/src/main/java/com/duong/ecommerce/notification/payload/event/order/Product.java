package com.duong.ecommerce.notification.payload.event.order;

import java.math.BigDecimal;

@org.springframework.data.annotation.TypeAlias("com.duong.ecommerce.kafka.order.Product")
public record Product(
        Integer productId,
        String name,
        String description,
        BigDecimal price,
        double quantity
) {
}
