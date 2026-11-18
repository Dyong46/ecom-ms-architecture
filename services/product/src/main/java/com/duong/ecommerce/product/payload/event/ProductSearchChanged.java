package com.duong.ecommerce.product.payload.event;

/** In-process notification; delivered to search only after the Product transaction commits. */
public record ProductSearchChanged(String productId) {
}
