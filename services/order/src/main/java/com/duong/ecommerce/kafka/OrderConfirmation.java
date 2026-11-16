package com.duong.ecommerce.kafka;

import com.duong.module.proxy.customer.response.CustomerResponse;
import com.duong.ecommerce.order.PaymentMethod;
import com.duong.module.proxy.product.response.PurchaseResponse;

import java.math.BigDecimal;
import java.util.List;

public record OrderConfirmation(
        String orderReference,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        CustomerResponse customer,
        List<PurchaseResponse> products,
        String eventId
) {
    public OrderConfirmation(
        String orderReference,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        CustomerResponse customer,
        List<PurchaseResponse> products
    ) {
        this(orderReference, totalAmount, paymentMethod, customer, products, java.util.UUID.randomUUID().toString());
    }
}
