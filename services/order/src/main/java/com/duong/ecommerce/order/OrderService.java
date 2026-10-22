package com.duong.ecommerce.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.duong.ecommerce.customer.CustomerClient;
import com.duong.ecommerce.exception.BusinessException;
import com.duong.ecommerce.kafka.OrderConfirmation;
import com.duong.ecommerce.kafka.OrderProducer;
import com.duong.ecommerce.orderline.OrderLineRequest;
import com.duong.ecommerce.orderline.OrderLineService;
import com.duong.ecommerce.payment.PaymentClient;
import com.duong.ecommerce.payment.PaymentRequest;
import com.duong.ecommerce.product.ProductClient;
import com.duong.ecommerce.product.PurchaseRequest;
import com.duong.ecommerce.product.PurchaseResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository repository;
    private final CustomerClient customerClient;
    private final ProductClient productClient;
    private final OrderMapper mapper;
    private final OrderLineService orderLineService;
    private final OrderProducer orderProducer;
    private final PaymentClient paymentClient;

    @Transactional
    public Integer createOrder(@Valid OrderRequest request) {
        // check the customer --> OpenFeign
        var customer = this.customerClient.findCustomerById(request.customerId())
                .orElseThrow(() -> new BusinessException("Cannot create order:: No Customer exists with the provided ID"));

        // purchase the products --> product-ms (RestTemplate)
        var purchaseProduct = this.productClient.purchaseResponses(request.products());
        var totalAmount = calculateTotalAmount(purchaseProduct);

        var order = this.repository.save(mapper.toOrder(request, totalAmount));

        // persist order
        for (PurchaseRequest purchaseRequest: request.products()) {
            orderLineService.saveOrderLine(
                    new OrderLineRequest(
                            null,
                            order.getId(),
                            purchaseRequest.productId(),
                            purchaseRequest.quantity()
                    )
            );
        }
        // start payment process
        var paymentRequest = new PaymentRequest(
                totalAmount,
                request.paymentMethod(),
                order.getId(),
                order.getReference(),
                customer
        );

        paymentClient.requestOrderPayment(paymentRequest);

        //send the order confirmation --> notification-ms (kafka)
        orderProducer.sendOrderConfirmation(
                new OrderConfirmation(
                        request.reference(),
                        totalAmount,
                        request.paymentMethod(),
                        customer,
                        purchaseProduct
                )
        );

        return order.getId();
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

    public Page<OrderResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::fromOrder);
    }

    public OrderResponse findById(Integer orderId) {
        return repository.findById(orderId)
                .map(mapper::fromOrder)
                .orElseThrow(() -> new EntityNotFoundException(String.format("No Order found with ID: %d", orderId)));
    }
}
