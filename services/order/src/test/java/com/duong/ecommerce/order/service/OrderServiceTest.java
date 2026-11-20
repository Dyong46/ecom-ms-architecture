package com.duong.ecommerce.order.service;

import com.duong.ecommerce.order.entity.Order;
import com.duong.ecommerce.order.entity.enumeration.PaymentMethodEnum;
import com.duong.ecommerce.order.exception.BusinessException;
import com.duong.ecommerce.order.integration.OrderPaymentGateway;
import com.duong.ecommerce.order.integration.kafka.producer.OrderProducer;
import com.duong.ecommerce.order.mapper.OrderMapper;
import com.duong.ecommerce.order.orderline.service.OrderLineService;
import com.duong.ecommerce.order.payload.request.OrderRequest;
import com.duong.ecommerce.order.repository.OrderRepository;
import com.duong.module.proxy.customer.CustomerClient;
import com.duong.module.proxy.customer.response.CustomerResponse;
import com.duong.module.proxy.product.ProductClient;
import com.duong.module.proxy.product.request.PurchaseRequest;
import com.duong.module.proxy.product.response.PurchaseResponse;
import com.duong.module.proxy.response.ServiceResponse;
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
import static org.mockito.Mockito.*;

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

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Spy
    private com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
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
                , "Other Recipient", "+84901234567", new com.duong.ecommerce.order.payload.request.ShippingAddressRequest("12 Main", null, "Ward", "City", null, "VN"), "Call on arrival");
        var customer = new CustomerResponse(
                "customer-1", "Ada", "Lovelace", "ada@example.com"
        );

        when(customerClient.findCustomerById("customer-1")).thenReturn(Optional.of(customer).map(value -> new ServiceResponse<>(value, null)));
        when(productClient.purchaseResponses("purchase:operation", request.products())).thenReturn(new ServiceResponse<>(List.of(
                new PurchaseResponse("00000000-0000-4000-8000-000000000065", "00000000-0000-4000-8000-000000000001", "SKU-1", "Keyboard", "", java.util.Map.of(), new BigDecimal("10.00"), 2),
                new PurchaseResponse("00000000-0000-4000-8000-000000000066", "00000000-0000-4000-8000-000000000002", "SKU-2", "Mouse", "", java.util.Map.of(), new BigDecimal("5.00"), 1)
        ), null));
        when(repository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(42);
            return order;
        });

        var draft = service.prepareOrder("operation", request);
        assertThat(draft.orderId()).isEqualTo(42);

        var orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(repository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getCustomerFullName()).isEqualTo("Ada Lovelace");
        assertThat(orderCaptor.getValue().getCustomerEmail()).isEqualTo("ada@example.com");
        assertThat(orderCaptor.getValue().getRecipientFullName()).isEqualTo("Other Recipient");
        assertThat(orderCaptor.getValue().getRecipientPhone()).isEqualTo("+84901234567");
        assertThat(orderCaptor.getValue().getShippingAddress().getAddressLine1()).isEqualTo("12 Main");
        assertThat(orderCaptor.getValue().getItemsSubtotal()).isEqualByComparingTo("25.00");
        assertThat(orderCaptor.getValue().getShippingFee()).isEqualByComparingTo("0.00");
        assertThat(orderCaptor.getValue().getCurrencyCode()).isEqualTo("VND");


        var lineCaptor = ArgumentCaptor.forClass(com.duong.ecommerce.order.orderline.payload.request.OrderLineRequest.class);
        org.mockito.Mockito.verify(orderLineService, org.mockito.Mockito.times(2)).saveOrderLine(lineCaptor.capture());
        assertThat(lineCaptor.getAllValues().get(0).skuId()).isEqualTo(request.products().get(0).skuId());
        assertThat(lineCaptor.getAllValues().get(0).sku()).isEqualTo("SKU-1");
        assertThat(lineCaptor.getAllValues().get(0).unitPrice()).isEqualByComparingTo("10.00");
        assertThat(draft.payment().amount()).isEqualByComparingTo("25.00");
        assertThat(draft.payment().paymentMethod().name()).isEqualTo(request.paymentMethod().name());


        assertThat(draft.confirmation().eventId()).isNotBlank();
        assertThat(draft.confirmation().totalAmount()).isEqualByComparingTo("25.00");
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
                , "Other Recipient", "+84901234567", new com.duong.ecommerce.order.payload.request.ShippingAddressRequest("12 Main", null, "Ward", "City", null, "VN"), "Call on arrival");
        when(customerClient.findCustomerById("customer-1")).thenReturn(Optional.of(
                new CustomerResponse("customer-1", "Ada", "Lovelace", "ada@example.com")
        ).map(value -> new ServiceResponse<>(value, null)));
        when(productClient.purchaseResponses("purchase:operation", request.products())).thenReturn(new ServiceResponse<>(List.of(
                response
        ), null));

        assertThatThrownBy(() -> service.prepareOrder("operation", request))
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
