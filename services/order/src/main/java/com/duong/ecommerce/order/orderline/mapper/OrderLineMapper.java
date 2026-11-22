package com.duong.ecommerce.order.orderline.mapper;

import com.duong.ecommerce.order.orderline.entity.OrderLine;
import com.duong.ecommerce.order.orderline.payload.request.OrderLineRequest;
import com.duong.ecommerce.order.orderline.payload.response.OrderLineResponse;
import org.mapstruct.BeforeMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.Objects;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrderLineMapper {
    @Mapping(target = "order.id", source = "orderId")
    @Mapping(target = "variantName", expression = "java(variantName(request))")
    OrderLine toOrderLine(OrderLineRequest request);

    default String variantName(OrderLineRequest request) {
        if (request.options() == null || request.options().isEmpty()) return request.sku();
        return new java.util.TreeMap<>(request.options()).entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(java.util.stream.Collectors.joining(" / "));
    }

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
