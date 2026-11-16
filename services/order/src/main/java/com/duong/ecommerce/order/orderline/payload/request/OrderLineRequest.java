package com.duong.ecommerce.order.orderline.payload.request;

public record OrderLineRequest(
        Integer id,
        Integer orderId,
        Integer productId,
        double quantity
) {
}
