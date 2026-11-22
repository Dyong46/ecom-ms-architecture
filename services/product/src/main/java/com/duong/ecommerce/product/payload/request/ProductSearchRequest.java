package com.duong.ecommerce.product.payload.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class ProductSearchRequest {
    @Size(max = 200)
    private String q;
    private Boolean hasStock;
    @NotNull @Size(max = 20)
    private List<@NotNull @Valid OptionFilter> options = new ArrayList<>();

    @Getter @Setter
    public static class OptionFilter {
        @NotBlank @Size(max = 100)
        private String name;
        @NotBlank @Size(max = 100)
        private String value;
    }
}
