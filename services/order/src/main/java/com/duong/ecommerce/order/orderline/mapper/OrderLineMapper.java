package com.duong.ecommerce.order.orderline.mapper;

import com.duong.ecommerce.order.orderline.entity.OrderLine;
import com.duong.ecommerce.order.orderline.payload.request.OrderLineRequest;
import com.duong.ecommerce.order.orderline.payload.response.OrderLineResponse;

import java.util.Objects;
import org.mapstruct.BeforeMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrderLineMapper {
    @Mapping(target = "order.id", source = "orderId")
    OrderLine toOrderLine(OrderLineRequest request);

    OrderLineResponse toOrderLineResponse(OrderLine orderLine);

    @BeforeMapping
    default void requireRequest(OrderLineRequest request) {
        Objects.requireNonNull(request, "request");
    }

    @BeforeMapping
    default void requireOrderLine(OrderLine orderLine) {
        Objects.requireNonNull(orderLine, "orderLine");
    }
}
