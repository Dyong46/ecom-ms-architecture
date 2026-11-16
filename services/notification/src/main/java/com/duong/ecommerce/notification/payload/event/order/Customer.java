package com.duong.ecommerce.notification.payload.event.order;

@org.springframework.data.annotation.TypeAlias("com.duong.ecommerce.kafka.order.Customer")
public record Customer(
        String id,
        String firstname,
        String lastname,
        String email
) {
}
