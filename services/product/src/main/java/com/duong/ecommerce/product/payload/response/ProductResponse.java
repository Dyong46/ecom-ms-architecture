package com.duong.ecommerce.product.payload.response;
import java.time.LocalDateTime;
import java.util.List;
public record ProductResponse(String id, String name, String slug, String description,
        LocalDateTime createdAt, LocalDateTime updatedAt, List<VariantResponse> variants) { }
