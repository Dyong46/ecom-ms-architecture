package com.duong.ecommerce.order.entity;

import com.duong.ecommerce.order.entity.enumeration.PaymentMethodEnum;
import com.duong.ecommerce.order.orderline.entity.OrderLine;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static jakarta.persistence.EnumType.STRING;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@org.hibernate.annotations.DynamicUpdate
@Builder
@EntityListeners(AuditingEntityListener.class)
@Table(name = "customer_order")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Builder.Default
    @Enumerated(STRING)
    @Column(nullable=false,length=16)
    private com.duong.ecommerce.order.entity.enumeration.PaymentStatusEnum paymentStatus=
            com.duong.ecommerce.order.entity.enumeration.PaymentStatusEnum.PENDING;

    @Builder.Default
    @Enumerated(STRING)
    @Column(nullable = false, length = 16)
    private com.duong.ecommerce.order.entity.enumeration.OrderCreationStatusEnum creationStatus =
            com.duong.ecommerce.order.entity.enumeration.OrderCreationStatusEnum.PROCESSING;

    private String reference;

    private BigDecimal totalAmount;

    @Column(precision = 38, scale = 2)
    private BigDecimal itemsSubtotal;

    @Column(precision = 38, scale = 2)
    private BigDecimal shippingFee;

    @Column(length = 3)
    private String currencyCode;

    @Enumerated(STRING)
    private PaymentMethodEnum paymentMethod;

    private String customerId;

    @Column(length = 511)
    private String customerFullName;

    @Column(length = 255)
    private String customerEmail;

    @Column(length = 255)
    private String recipientFullName;

    @Column(length = 32)
    private String recipientPhone;

    @Embedded
    private ShippingAddress shippingAddress;

    @Column(length = 1000)
    private String deliveryNote;

    @OneToMany(mappedBy = "order")
    private List<OrderLine> orderLines;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createAt;

    @LastModifiedDate
    @Column(insertable = false)
    private LocalDateTime lastModifiedDate;
}
