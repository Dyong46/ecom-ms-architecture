package com.duong.ecommerce.payment.entity;

import com.duong.ecommerce.payment.entity.enumeration.PaymentMethodEnum;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static jakarta.persistence.EnumType.STRING;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private BigDecimal amount;

    @Enumerated(STRING)
    private PaymentMethodEnum paymentMethod;

    private Integer orderId;

    @Column(length = 3)
    private String currencyCode;

    @Enumerated(STRING)
    @Column(length = 16)
    private com.duong.ecommerce.payment.entity.enumeration.PaymentStatusEnum status;

    private String orderReference;
    private String customerFirstname;
    private String customerLastname;
    private String customerEmail;
    private java.time.Instant paidAt;

    public void initializeCheckout() {
        currencyCode="VND";
        status=com.duong.ecommerce.payment.entity.enumeration.PaymentStatusEnum.PENDING;
    }

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createDate;

    @LastModifiedDate
    @Column(insertable = false)
    private LocalDateTime lastModifiedDate;
}
