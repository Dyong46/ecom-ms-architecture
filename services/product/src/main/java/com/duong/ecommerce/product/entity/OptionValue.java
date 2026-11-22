package com.duong.ecommerce.product.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(uniqueConstraints = @UniqueConstraint(columnNames = {"option_id", "option_value"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OptionValue {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36, updatable = false)
    private String id;
    @Column(name = "option_value", nullable = false, length = 100)
    private String value;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")
    private JsonNode synonyms;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false)
    private Option option;
}
