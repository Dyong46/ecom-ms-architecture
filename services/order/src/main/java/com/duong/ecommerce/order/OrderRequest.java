package com.duong.ecommerce.order;

import com.duong.ecommerce.product.PurchaseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record OrderRequest(

        Integer id,

        @NotBlank(message = "Order reference is required")
        String reference,

        @NotNull(message = "Payment method should be precised")
        PaymentMethod paymentMethod,

        @NotNull(message = "Customer should be present")
        @NotEmpty(message = "Customer should be present")
        @NotBlank(message = "Customer should be present")
        String customerId,

        @NotEmpty(message = "You should at least purchase one product")
        List<@Valid PurchaseRequest> products
) {
}
