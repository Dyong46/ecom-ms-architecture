package com.duong.ecommerce.order.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShippingAddressRequest(
        @NotBlank @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @NotBlank @Size(max = 100) String ward,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 32) String postalCode,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryCode
) {
}
