package com.duong.ecommerce.product.payload.request;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
public record OptionValueRequest(@NotBlank @Size(max = 100) String value, JsonNode synonyms) { }
