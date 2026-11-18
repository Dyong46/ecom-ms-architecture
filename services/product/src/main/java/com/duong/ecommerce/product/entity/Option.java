package com.duong.ecommerce.product.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "product_option")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Option {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36, updatable = false)
    private String id;
    @Column(nullable = false, unique = true, length = 100)
    private String name;
    @Builder.Default
    @OneToMany(mappedBy = "option", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OptionValue> values = new ArrayList<>();
}
