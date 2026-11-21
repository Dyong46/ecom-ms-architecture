package com.duong.ecommerce.order.service;


import com.duong.ecommerce.order.exception.BusinessException;
import com.duong.ecommerce.order.mapper.OrderMapper;
import com.duong.ecommerce.order.orderline.payload.request.OrderLineRequest;
import com.duong.ecommerce.order.orderline.service.OrderLineService;
import com.duong.ecommerce.order.payload.event.OrderConfirmation;
import com.duong.ecommerce.order.payload.request.OrderRequest;
import com.duong.ecommerce.order.payload.response.OrderResponse;
import com.duong.ecommerce.order.repository.OrderRepository;
import com.duong.module.proxy.customer.CustomerClient;
import com.duong.module.proxy.payment.request.PaymentRequest;
import com.duong.module.proxy.product.ProductClient;
import com.duong.module.proxy.product.request.PurchaseRequest;
import com.duong.module.proxy.product.response.PurchaseResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository repository;
    private final com.duong.ecommerce.order.integration.OrderPaymentGateway payments;
    private final CustomerClient customerClient;
    private final ProductClient productClient;
    private final OrderMapper mapper;
    private final OrderLineService orderLineService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public CheckoutDraft prepareOrder(String operationId, @Valid OrderRequest request) {
        var previous = jdbc.query("select context_json from order_checkout_draft where operation_id = ?",
                (row, index) -> row.getString(1), operationId);
        if (!previous.isEmpty()) {
            try {
                return json.readValue(previous.get(0), CheckoutDraft.class);
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot read checkout snapshot", exception);
            }
        }
        // check the customer --> OpenFeign
        var customer = this.customerClient.findCustomerById(request.customerId())
                .map(com.duong.module.proxy.response.ServiceResponse::data)
                .orElseThrow(() -> new BusinessException("Cannot create order:: No Customer exists with the provided ID"));

        // Purchase through Product's internal API using the shared Feign client.
        var purchaseResponse = this.productClient.purchaseResponses("purchase:" + operationId, request.products());
        var purchaseProduct = purchaseResponse == null ? null : purchaseResponse.data();
        var itemsSubtotal = calculateTotalAmount(purchaseProduct);
        // Shipping pricing is deferred. This placeholder is owned by Order, never by the client.
        var shippingFee = new BigDecimal("0.00");
        var totalAmount = itemsSubtotal.add(shippingFee);
        var requestedSkus = request.products().stream().collect(java.util.stream.Collectors.toMap(
                PurchaseRequest::skuId, PurchaseRequest::quantity));
        if (purchaseProduct.size() != requestedSkus.size()
                || purchaseProduct.stream().map(com.duong.module.proxy.product.response.PurchaseResponse::skuId)
                        .distinct().count() != purchaseProduct.size()
                || purchaseProduct.stream().anyMatch(p -> p.skuId() == null || p.productId() == null
                        || p.sku() == null || p.name() == null || p.options() == null
                        || !java.util.Objects.equals(requestedSkus.get(p.skuId()), p.quantity()))) {
            throw new BusinessException("Cannot create order:: Product service returned mismatched SKUs");
        }

        var orderSnapshot = mapper.toOrder(request, totalAmount);
        orderSnapshot.setItemsSubtotal(itemsSubtotal);
        orderSnapshot.setShippingFee(shippingFee);
        orderSnapshot.setCurrencyCode("VND");
        orderSnapshot.setCustomerFullName(java.util.stream.Stream.of(customer.firstname(), customer.lastname())
                .filter(java.util.Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.joining(" ")));
        orderSnapshot.setCustomerEmail(customer.email());
        var order = this.repository.save(orderSnapshot);

        // persist order
        for (var purchased : purchaseProduct) {
            orderLineService.saveOrderLine(new OrderLineRequest(null, order.getId(),
                    purchased.productId(), purchased.skuId(), purchased.sku(), purchased.name(),
                    java.util.Map.copyOf(purchased.options()), purchased.price(), purchased.quantity()));
        }
        // start payment process
        var paymentRequest = new PaymentRequest(
                totalAmount,
                com.duong.module.proxy.payment.entity.enumeration.PaymentMethodEnum.valueOf(request.paymentMethod().name()),
                order.getId(),
                order.getReference(),
                customer
        );

        var draft = new CheckoutDraft(order.getId(), paymentRequest,
                new OrderConfirmation(request.reference(), totalAmount, request.paymentMethod(), customer, purchaseProduct));
        try {
            jdbc.update("insert into order_checkout_draft(operation_id, order_id, context_json) values (?, ?, ?)",
                    operationId, order.getId(), json.writeValueAsString(draft));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Cannot store checkout snapshot", exception);
        }
        return draft;
    }

    private BigDecimal calculateTotalAmount(List<PurchaseResponse> purchasedProducts) {
        if (purchasedProducts == null || purchasedProducts.isEmpty()) {
            throw new BusinessException("Cannot create order:: Product service returned no purchased products");
        }

        return purchasedProducts.stream()
                .map(product -> {
                    if (product.price() == null || product.price().signum() < 0 || product.quantity() <= 0) {
                        throw new BusinessException("Cannot create order:: Product service returned invalid pricing data");
                    }
                    return product.price().multiply(BigDecimal.valueOf(product.quantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public com.duong.module.proxy.payment.response.PaymentCheckoutResponse payment(int orderId) {
        if(!repository.existsById(orderId)) throw new EntityNotFoundException("Order not found");
        return payments.checkoutByOrder(orderId);
    }
    public String retryPayment(int orderId,String key) {
        var checkout=payment(orderId);
        return payments.retry(key,checkout.paymentId());
    }

    public Page<OrderResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::fromOrder);
    }

    public OrderResponse findById(Integer orderId) {
        return repository.findById(orderId)
                .map(mapper::fromOrder)
                .orElseThrow(() -> new EntityNotFoundException(String.format("No Order found with ID: %d", orderId)));
    }
}
