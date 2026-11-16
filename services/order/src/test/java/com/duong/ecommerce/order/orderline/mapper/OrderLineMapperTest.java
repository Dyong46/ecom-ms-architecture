package com.duong.ecommerce.order.orderline.mapper;

import com.duong.ecommerce.order.orderline.payload.request.OrderLineRequest;
import com.duong.ecommerce.order.orderline.payload.response.OrderLineResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.*;

@SpringJUnitConfig(OrderLineMapperImpl.class)
class OrderLineMapperTest {
    @Autowired OrderLineMapper mapper;

    @Test
    void createsAnOrderReferenceWithoutCopyingOrderLineFieldsIntoOrder() {
        var line = mapper.toOrderLine(new OrderLineRequest(3, 9, 7, 2.5));
        assertThat(line.getId()).isEqualTo(3);
        assertThat(line.getProductId()).isEqualTo(7);
        assertThat(line.getOrder().getId()).isEqualTo(9);
        assertThat(line.getOrder().getReference()).isNull();
        assertThat(mapper.toOrderLineResponse(line)).isEqualTo(new OrderLineResponse(3, 2.5));
    }

    @Test
    void preservesEmptyOrderReferenceAndRejectsNullSources() {
        var line = mapper.toOrderLine(new OrderLineRequest(null, null, null, 0));
        assertThat(line.getOrder()).isNotNull();
        assertThat(line.getOrder().getId()).isNull();
        assertThatNullPointerException().isThrownBy(() -> mapper.toOrderLine(null));
        assertThatNullPointerException().isThrownBy(() -> mapper.toOrderLineResponse(null));
    }
}
