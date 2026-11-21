package com.duong.ecommerce.payment.payload.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Customer(
        String id,

        @NotBlank(message = "Firstname is required")
        @Size(max=255) String firstname,

        @NotBlank(message = "Lastname is required")
        @Size(max=255) String lastname,

        @NotBlank(message = "Email is required")
        @Email(message = "The customer is not correctly formatted")
        @Size(max=255) String email
) {
}
