package com.duong.ecommerce.order.mapper;

import com.duong.ecommerce.order.entity.Order;
import com.duong.ecommerce.order.payload.request.OrderRequest;
import com.duong.ecommerce.order.payload.response.OrderResponse;
import jakarta.validation.Valid;
import org.mapstruct.BeforeMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.util.Objects;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrderMapper {

    @Mapping(target = "totalAmount", source = "totalAmount")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creationStatus", ignore = true)
    @Mapping(target = "paymentStatus", ignore = true)
    @Mapping(target = "itemsSubtotal", ignore = true)
    @Mapping(target = "shippingFee", ignore = true)
    @Mapping(target = "currencyCode", ignore = true)
    @Mapping(target = "customerFullName", ignore = true)
    @Mapping(target = "customerEmail", ignore = true)
    @Mapping(target = "orderLines", ignore = true)
    @Mapping(target = "createAt", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    Order toOrder(@Valid OrderRequest request, BigDecimal totalAmount);

    @Mapping(target = "amount", source = "totalAmount")
    OrderResponse fromOrder(Order order);

    @BeforeMapping
    default void requireRequest(OrderRequest request) {
        Objects.requireNonNull(request, "request");
    }

    @BeforeMapping
    default void requireOrder(Order order) {
        Objects.requireNonNull(order, "order");
    }
}
