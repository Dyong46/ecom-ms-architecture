package com.duong.ecommerce.order.orderline.payload.response;
import java.math.BigDecimal;
import java.util.Map;
public record OrderLineResponse(Integer id, String productId, String skuId,
                                String sku,
                                String name,
                                Map<String, String> options,
                                BigDecimal unitPrice,
                                int quantity,
                                String variantName
) {
}
