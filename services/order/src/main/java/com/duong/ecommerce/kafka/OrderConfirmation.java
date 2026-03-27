package com.duong.ecommerce.kafka;

import com.duong.ecommerce.customer.CustomerResponse;
import com.duong.ecommerce.order.PaymentMethod;
import com.duong.ecommerce.product.PurchaseResponse;

import java.math.BigDecimal;
import java.util.List;

public record OrderConfirmation(
        String orderReference,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        CustomerResponse customer,
        List<PurchaseResponse> products
) {
}
