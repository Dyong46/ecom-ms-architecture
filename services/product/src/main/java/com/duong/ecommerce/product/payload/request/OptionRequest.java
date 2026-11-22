package com.duong.ecommerce.product.payload.request;
import jakarta.validation.constraints.*;
public record OptionRequest(@NotBlank @Size(max = 100) String name) { }
