package com.duong.ecommerce.product.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

@Embeddable @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class ProductVariantOptionValueId implements Serializable {
    @Column(name = "product_variant_id", length = 36)
    private String productVariantId;
    @Column(name = "option_value_id", length = 36)
    private String optionValueId;
}
