package com.duong.ecommerce.notification.payload.event.order;

import com.duong.ecommerce.notification.entity.enumeration.PaymentMethodEnum;

import java.math.BigDecimal;
import java.util.List;

@org.springframework.data.annotation.TypeAlias("com.duong.ecommerce.kafka.order.OrderConfirmation")
public record OrderConfirmation(
        String orderReference,
        BigDecimal totalAmount,
        PaymentMethodEnum paymentMethod,
        Customer customer,
        List<Product> products,
        String eventId
) {
}
