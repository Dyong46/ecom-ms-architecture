package com.duong.ecommerce.product.payload.response;
import java.util.List;
public record OptionResponse(String id, String name, List<OptionValueResponse> values) { }
