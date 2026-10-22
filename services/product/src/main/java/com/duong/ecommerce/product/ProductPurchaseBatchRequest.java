package com.duong.ecommerce.product;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ProductPurchaseBatchRequest(
        @NotEmpty List<@NotNull @Valid ProductPurchaseRequest> products
) {
    // Keep the existing JSON array contract while validating a request object.
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public ProductPurchaseBatchRequest {
    }
}
