package com.duong.ecommerce.order.service;

import com.duong.ecommerce.order.entity.Order;
import com.duong.ecommerce.order.entity.enumeration.PaymentMethodEnum;
import com.duong.ecommerce.order.mapper.OrderMapper;
import com.duong.ecommerce.order.payload.request.OrderRequest;
import com.duong.ecommerce.order.repository.OrderRepository;

import com.duong.module.proxy.customer.CustomerClient;
import com.duong.module.proxy.response.ServiceResponse;
import com.duong.module.proxy.customer.response.CustomerResponse;
import com.duong.ecommerce.order.exception.BusinessException;
import com.duong.ecommerce.order.payload.event.OrderConfirmation;
import com.duong.ecommerce.order.integration.kafka.producer.OrderProducer;
import com.duong.ecommerce.order.orderline.service.OrderLineService;
import com.duong.ecommerce.order.integration.OrderPaymentGateway;
import com.duong.module.proxy.payment.request.PaymentRequest;
import com.duong.module.proxy.product.ProductClient;
import com.duong.module.proxy.product.request.PurchaseRequest;
import com.duong.module.proxy.product.response.PurchaseResponse;
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
    private OrderMapper mapper = new com.duong.ecommerce.order.mapper.OrderMapperImpl();
    @Mock
    private OrderLineService orderLineService;
    @Mock
    private OrderProducer orderProducer;
    @Mock
    private OrderPaymentGateway paymentClient;

    @InjectMocks
    private OrderService service;

    @Test
    void calculatesTrustedTotalFromPurchasedProducts() {
        var request = new OrderRequest(
                null,
                "ORDER-001",
                PaymentMethodEnum.CREDIT_CARD,
                "customer-1",
                List.of(new PurchaseRequest("00000000-0000-4000-8000-000000000001", 2), new PurchaseRequest("00000000-0000-4000-8000-000000000002", 1))
        );
        var customer = new CustomerResponse(
                "customer-1", "Ada", "Lovelace", "ada@example.com"
        );

        when(customerClient.findCustomerById("customer-1")).thenReturn(Optional.of(customer).map(value -> new ServiceResponse<>(value, null)));
        when(productClient.purchaseResponses(request.products())).thenReturn(new ServiceResponse<>(List.of(
                new PurchaseResponse("00000000-0000-4000-8000-000000000065", "00000000-0000-4000-8000-000000000001", "SKU-1", "Keyboard", "", java.util.Map.of(), new BigDecimal("10.00"), 2),
                new PurchaseResponse("00000000-0000-4000-8000-000000000066", "00000000-0000-4000-8000-000000000002", "SKU-2", "Mouse", "", java.util.Map.of(), new BigDecimal("5.00"), 1)
        ), null));
        when(repository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(42);
            return order;
        });

        assertThat(service.createOrder(request)).isEqualTo(42);

        var paymentCaptor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentClient).requestOrderPayment(paymentCaptor.capture());
        var lineCaptor = ArgumentCaptor.forClass(com.duong.ecommerce.order.orderline.payload.request.OrderLineRequest.class);
        org.mockito.Mockito.verify(orderLineService, org.mockito.Mockito.times(2)).saveOrderLine(lineCaptor.capture());
        assertThat(lineCaptor.getAllValues().get(0).skuId()).isEqualTo(request.products().get(0).skuId());
        assertThat(lineCaptor.getAllValues().get(0).sku()).isEqualTo("SKU-1");
        assertThat(lineCaptor.getAllValues().get(0).unitPrice()).isEqualByComparingTo("10.00");
        assertThat(paymentCaptor.getValue().amount()).isEqualByComparingTo("25.00");
        assertThat(paymentCaptor.getValue().paymentMethod().name()).isEqualTo(request.paymentMethod().name());

        var eventCaptor = ArgumentCaptor.forClass(OrderConfirmation.class);
        verify(orderProducer).sendOrderConfirmation(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventId()).isNotBlank();
        assertThat(eventCaptor.getValue().totalAmount()).isEqualByComparingTo("25.00");
    }

    @ParameterizedTest
    @MethodSource("invalidPricingResponses")
    void rejectsInvalidPricingReturnedByProductService(PurchaseResponse response) {
        var request = new OrderRequest(
                null,
                "ORDER-002",
                PaymentMethodEnum.CREDIT_CARD,
                "customer-1",
                List.of(new PurchaseRequest("00000000-0000-4000-8000-000000000001", 1))
        );
        when(customerClient.findCustomerById("customer-1")).thenReturn(Optional.of(
                new CustomerResponse("customer-1", "Ada", "Lovelace", "ada@example.com")
        ).map(value -> new ServiceResponse<>(value, null)));
        when(productClient.purchaseResponses(request.products())).thenReturn(new ServiceResponse<>(List.of(
                response
        ), null));

        assertThatThrownBy(() -> service.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("msg", "Cannot create order:: Product service returned invalid pricing data");
        verifyNoInteractions(repository, orderLineService, paymentClient, orderProducer);
    }

    private static List<PurchaseResponse> invalidPricingResponses() {
        return List.of(
                new PurchaseResponse("00000000-0000-4000-8000-000000000065", "00000000-0000-4000-8000-000000000001", "SKU-1", "Keyboard", "", java.util.Map.of(), null, 1),
                new PurchaseResponse("00000000-0000-4000-8000-000000000065", "00000000-0000-4000-8000-000000000001", "SKU-1", "Keyboard", "", java.util.Map.of(), new BigDecimal("-0.01"), 1),
                new PurchaseResponse("00000000-0000-4000-8000-000000000065", "00000000-0000-4000-8000-000000000001", "SKU-1", "Keyboard", "", java.util.Map.of(), BigDecimal.TEN, 0),
                new PurchaseResponse("00000000-0000-4000-8000-000000000065", "00000000-0000-4000-8000-000000000001", "SKU-1", "Keyboard", "", java.util.Map.of(), BigDecimal.TEN, -1)
        );
    }
}
