package com.duong.ecommerce.product;

import com.duong.ecommerce.category.Category;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@lombok.experimental.SuperBuilder
@Getter
@Setter
@Entity
@org.hibernate.annotations.SQLDelete(sql = "UPDATE product SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
public class Product extends com.duong.module.data.model.entity.BaseEntity<Integer> {

    @Id
    @GeneratedValue
    private Integer id;

    private String name;

    private String description;

    private double availableQuantity;

    private BigDecimal price;

    @ManyToOne
    @JoinColumn(name = "category_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"products", "hibernateLazyInitializer", "handler"})
    private Category category;
}
