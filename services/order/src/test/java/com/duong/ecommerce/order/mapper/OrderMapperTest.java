package com.duong.ecommerce.order.mapper;

import com.duong.ecommerce.order.entity.enumeration.PaymentMethodEnum;
import com.duong.ecommerce.order.payload.request.OrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@SpringJUnitConfig(OrderMapperImpl.class)
class OrderMapperTest {
    @Autowired OrderMapper mapper;

    @Test
    void usesCalculatedTotalAndExposesItAsResponseAmount() {
        var request = new OrderRequest(9, "ORDER-9", PaymentMethodEnum.PAYPAL, "customer-1", List.of(), "Other Recipient", "+84901234567", new com.duong.ecommerce.order.payload.request.ShippingAddressRequest("12 Main", null, "Ward", "City", null, "VN"), "Call on arrival");
        var order = mapper.toOrder(request, new BigDecimal("99.98"));
        var response = mapper.fromOrder(order);
        assertThat(response.id()).isNull();
        assertThat(response.reference()).isEqualTo("ORDER-9");
        assertThat(response.amount()).isEqualByComparingTo("99.98");
        assertThat(response.recipientFullName()).isEqualTo(request.recipientFullName());
        assertThat(response.recipientPhone()).isEqualTo(request.recipientPhone());
        assertThat(response.shippingAddress().addressLine1()).isEqualTo("12 Main");
        assertThat(response.deliveryNote()).isEqualTo("Call on arrival");
        assertThat(order.getOrderLines()).isNull();
        assertThat(order.getCreateAt()).isNull();
        assertThat(order.getLastModifiedDate()).isNull();
        assertThat(mapper.toOrder(request, null).getTotalAmount()).isNull();
    }

    @Test
    void preservesRejectionOfNullSources() {
        assertThatNullPointerException().isThrownBy(() -> mapper.toOrder(null, BigDecimal.TEN));
        assertThatNullPointerException().isThrownBy(() -> mapper.toOrder(null, null));
        assertThatNullPointerException().isThrownBy(() -> mapper.fromOrder(null));
    }
}
