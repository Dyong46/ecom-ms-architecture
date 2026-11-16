package com.duong.ecommerce.order.mapper;

import com.duong.ecommerce.order.enumtype.PaymentMethod;
import com.duong.ecommerce.order.payload.request.OrderRequest;
import com.duong.ecommerce.order.payload.response.OrderResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.*;

@SpringJUnitConfig(OrderMapperImpl.class)
class OrderMapperTest {
    @Autowired OrderMapper mapper;

    @Test
    void usesCalculatedTotalAndExposesItAsResponseAmount() {
        var request = new OrderRequest(9, "ORDER-9", PaymentMethod.PAYPAL, "customer-1", List.of());
        var order = mapper.toOrder(request, new BigDecimal("99.98"));
        assertThat(mapper.fromOrder(order)).isEqualTo(new OrderResponse(
                9, "ORDER-9", new BigDecimal("99.98"), PaymentMethod.PAYPAL, "customer-1"));
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
