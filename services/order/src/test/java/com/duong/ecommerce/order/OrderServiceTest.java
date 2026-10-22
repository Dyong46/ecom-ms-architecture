package com.duong.ecommerce.order;

import com.duong.ecommerce.customer.CustomerClient;
import com.duong.ecommerce.customer.CustomerResponse;
import com.duong.ecommerce.exception.BusinessException;
import com.duong.ecommerce.kafka.OrderConfirmation;
import com.duong.ecommerce.kafka.OrderProducer;
import com.duong.ecommerce.orderline.OrderLineService;
import com.duong.ecommerce.payment.PaymentClient;
import com.duong.ecommerce.payment.PaymentRequest;
import com.duong.ecommerce.product.ProductClient;
import com.duong.ecommerce.product.PurchaseRequest;
import com.duong.ecommerce.product.PurchaseResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository repository;
    @Mock
    private CustomerClient customerClient;
    @Mock
    private ProductClient productClient;
    @Spy
    private OrderMapper mapper;
    @Mock
    private OrderLineService orderLineService;
    @Mock
    private OrderProducer orderProducer;
    @Mock
    private PaymentClient paymentClient;

    @InjectMocks
    private OrderService service;

    @Test
    void calculatesTrustedTotalFromPurchasedProducts() {
        var request = new OrderRequest(
                null,
                "ORDER-001",
                PaymentMethod.CREDIT_CARD,
                "customer-1",
                List.of(new PurchaseRequest(1, 2), new PurchaseRequest(2, 1))
        );
        var customer = new CustomerResponse(
                "customer-1", "Ada", "Lovelace", "ada@example.com"
        );

        when(customerClient.findCustomerById("customer-1")).thenReturn(Optional.of(customer));
        when(productClient.purchaseResponses(request.products())).thenReturn(List.of(
                new PurchaseResponse(1, "Keyboard", "", new BigDecimal("10.00"), 2),
                new PurchaseResponse(2, "Mouse", "", new BigDecimal("5.00"), 1)
        ));
        when(repository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(42);
            return order;
        });

        assertThat(service.createOrder(request)).isEqualTo(42);

        var paymentCaptor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentClient).requestOrderPayment(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().amount()).isEqualByComparingTo("25.00");

        var eventCaptor = ArgumentCaptor.forClass(OrderConfirmation.class);
        verify(orderProducer).sendOrderConfirmation(eventCaptor.capture());
        assertThat(eventCaptor.getValue().totalAmount()).isEqualByComparingTo("25.00");
    }

    @ParameterizedTest
    @MethodSource("invalidPricingResponses")
    void rejectsInvalidPricingReturnedByProductService(PurchaseResponse response) {
        var request = new OrderRequest(
                null,
                "ORDER-002",
                PaymentMethod.CREDIT_CARD,
                "customer-1",
                List.of(new PurchaseRequest(1, 1))
        );
        when(customerClient.findCustomerById("customer-1")).thenReturn(Optional.of(
                new CustomerResponse("customer-1", "Ada", "Lovelace", "ada@example.com")
        ));
        when(productClient.purchaseResponses(request.products())).thenReturn(List.of(
                response
        ));

        assertThatThrownBy(() -> service.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("msg", "Cannot create order:: Product service returned invalid pricing data");
        verifyNoInteractions(repository, orderLineService, paymentClient, orderProducer);
    }

    private static List<PurchaseResponse> invalidPricingResponses() {
        return List.of(
                new PurchaseResponse(1, "Keyboard", "", null, 1),
                new PurchaseResponse(1, "Keyboard", "", new BigDecimal("-0.01"), 1),
                new PurchaseResponse(1, "Keyboard", "", BigDecimal.TEN, 0),
                new PurchaseResponse(1, "Keyboard", "", BigDecimal.TEN, -1)
        );
    }
}
