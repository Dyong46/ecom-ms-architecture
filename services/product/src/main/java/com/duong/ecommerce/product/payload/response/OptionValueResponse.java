package com.duong.ecommerce.product.payload.response;
import com.fasterxml.jackson.databind.JsonNode;
public record OptionValueResponse(String id, String value, JsonNode synonyms) { }
