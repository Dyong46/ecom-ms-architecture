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
        var line = mapper.toOrderLine(new OrderLineRequest(3, 9, "00000000-0000-4000-8000-000000000065", "00000000-0000-4000-8000-000000000001", "SKU-1", "Keyboard", java.util.Map.of("Color", "Blue"), java.math.BigDecimal.TEN, 2));
        assertThat(line.getId()).isEqualTo(3);
        assertThat(line.getProductId()).isEqualTo("00000000-0000-4000-8000-000000000065");
        assertThat(line.getOrder().getId()).isEqualTo(9);
        assertThat(line.getOrder().getReference()).isNull();
        assertThat(mapper.toOrderLineResponse(line)).isEqualTo(new OrderLineResponse(3, "00000000-0000-4000-8000-000000000065", "00000000-0000-4000-8000-000000000001", "SKU-1", "Keyboard", java.util.Map.of("Color", "Blue"), java.math.BigDecimal.TEN, 2));
    }

    @Test
    void preservesEmptyOrderReferenceAndRejectsNullSources() {
        var line = mapper.toOrderLine(new OrderLineRequest(null, null, null, null, null, null, null, null, 0));
        assertThat(line.getOrder()).isNotNull();
        assertThat(line.getOrder().getId()).isNull();
        assertThatNullPointerException().isThrownBy(() -> mapper.toOrderLine(null));
        assertThatNullPointerException().isThrownBy(() -> mapper.toOrderLineResponse(null));
    }
}
