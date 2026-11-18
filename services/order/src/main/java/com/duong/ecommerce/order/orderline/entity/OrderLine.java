package com.duong.ecommerce.order.orderline.entity;
import com.duong.ecommerce.order.entity.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.util.Map;
@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @Column(nullable = false, length = 36)
    private String productId;
    @Column(nullable = false, length = 36)
    private String skuId;
    @Column(nullable = false)
    private String sku;
    @Column(nullable = false)
    private String name;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> options;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;
    @Column(nullable = false)
    private int quantity;
}
