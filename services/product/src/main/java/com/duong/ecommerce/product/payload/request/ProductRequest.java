package com.duong.ecommerce.product.payload.request;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record ProductRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String slug,
        String description,
        @NotNull List<@NotNull @Valid VariantRequest> variants) { }
