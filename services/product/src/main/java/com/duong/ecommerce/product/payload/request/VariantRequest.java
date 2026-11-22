package com.duong.ecommerce.product.payload.request;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.UUID;
import java.math.BigDecimal;
import java.util.List;
public record VariantRequest(
        @NotBlank @Size(max = 255) String sku,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @PositiveOrZero int stockQuantity,
        @NotNull List<@NotBlank @UUID String> optionValueIds) { }
