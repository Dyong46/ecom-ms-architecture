package com.duong.ecommerce.order.service;

import com.duong.ecommerce.order.entity.enumeration.OrderCreationStatusEnum;
import com.duong.ecommerce.order.exception.BusinessException;
import com.duong.ecommerce.order.integration.OrderPaymentGateway;
import com.duong.ecommerce.order.integration.idempotency.PostgresIdempotencyStore;
import com.duong.ecommerce.order.integration.kafka.producer.OrderProducer;
import com.duong.ecommerce.order.payload.request.OrderRequest;
import com.duong.ecommerce.order.repository.OrderRepository;
import com.duong.module.proxy.product.request.PurchaseRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;

import java.util.Comparator;

@Service
@Validated
@RequiredArgsConstructor
public class OrderCheckoutService {
    private final PostgresIdempotencyStore idempotency;
    private final OrderService orders;
    private final OrderRepository repository;
    private final OrderPaymentGateway payments;
    private final OrderProducer events;
    private final ObjectMapper json;

    public Integer create(String key, @NotNull @Valid OrderRequest request) {
        ObjectNode canonical = json.valueToTree(request);
        canonical.remove("id"); // Ignored by creation, so it must not change the fingerprint.
        canonical.set("products", json.valueToTree(request.products().stream()
                .sorted(Comparator.comparing(PurchaseRequest::skuId)).toList()));
        return idempotency.execute(key, request.reference(), canonical, new TypeReference<Integer>() {
        }, operationId -> {
            var draft = orders.prepareOrder(operationId, request);
            var paymentId = payments.requestOrderPayment("payment:" + operationId, draft.payment());
            if (paymentId == null || paymentId <= 0)
                throw new BusinessException("Payment service returned no payment ID");
            var order = repository.findById(draft.orderId()).orElseThrow();
            order.setCreationStatus(OrderCreationStatusEnum.COMPLETED);
            // No outbox in this scope: emit once after this local completion commits, never on replay.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    events.sendOrderConfirmation(draft.confirmation());
                }
            });
            return draft.orderId();
        });
    }
}
