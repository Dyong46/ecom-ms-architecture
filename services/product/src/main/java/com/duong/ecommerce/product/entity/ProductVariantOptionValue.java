package com.duong.ecommerce.product.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductVariantOptionValue {
    @EmbeddedId
    private ProductVariantOptionValueId id;
    @MapsId("productVariantId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant productVariant;
    @MapsId("optionValueId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_value_id", nullable = false)
    private OptionValue optionValue;
}
