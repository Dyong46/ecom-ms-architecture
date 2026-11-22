package com.duong.ecommerce.order.payload.request;

import com.duong.ecommerce.order.entity.enumeration.PaymentMethodEnum;
import com.duong.module.proxy.product.request.PurchaseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record OrderRequest(

        Integer id,

        @NotBlank(message = "Order reference is required")
        String reference,

        @NotNull(message = "Payment method should be precised")
        PaymentMethodEnum paymentMethod,

        @NotNull(message = "Customer should be present")
        @NotEmpty(message = "Customer should be present")
        @NotBlank(message = "Customer should be present")
        String customerId,

        @NotEmpty(message = "You should at least purchase one product")
        List<@NotNull @Valid PurchaseRequest> products,

        @NotBlank @Size(max = 255) String recipientFullName,
        @NotBlank @Size(max = 32) @Pattern(regexp = "\\+?[0-9][0-9 ()-]{5,30}") String recipientPhone,
        @NotNull @Valid ShippingAddressRequest shippingAddress,
        @Size(max = 1000) String deliveryNote
) {
    @com.fasterxml.jackson.annotation.JsonIgnore
    @AssertTrue(message="Only Stripe card payments are currently supported")
    public boolean isSupportedPaymentMethod() {
        return paymentMethod==null || java.util.Set.of(PaymentMethodEnum.CREDIT_CARD,PaymentMethodEnum.VISA,
                PaymentMethodEnum.MASTER_CARD).contains(paymentMethod);
    }
}
