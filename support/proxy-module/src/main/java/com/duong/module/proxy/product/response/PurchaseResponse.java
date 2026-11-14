package com.duong.module.proxy.product.response;

import java.math.BigDecimal;

public record PurchaseResponse(
    Integer productId,
    String name,
    String description,
    BigDecimal price,
    double quantity
) {
}
