package com.duong.ecommerce.order.service;

import com.duong.ecommerce.order.payload.event.OrderConfirmation;
import com.duong.module.proxy.payment.request.PaymentRequest;

/**
 * Durable outbound snapshots used unchanged when checkout resumes.
 */
public record CheckoutDraft(Integer orderId, PaymentRequest payment, OrderConfirmation confirmation) {
}
