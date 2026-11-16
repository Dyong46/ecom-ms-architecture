package com.duong.ecommerce.payment.mapper;

import com.duong.ecommerce.payment.entity.Payment;
import com.duong.ecommerce.payment.entity.enumeration.PaymentMethodEnum;
import com.duong.ecommerce.payment.payload.request.PaymentRequest;
import com.duong.ecommerce.payment.payload.response.PaymentResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.*;

@SpringJUnitConfig(PaymentMapperImpl.class)
class PaymentMapperTest {
    @Autowired PaymentMapper mapper;

    @Test
    void requestMappingLeavesAuditingToPersistence() {
        var payment = mapper.toPayment(new PaymentRequest(
                4, new BigDecimal("99.98"), PaymentMethodEnum.PAYPAL, 9, "ORDER-9", null));
        assertThat(payment.getId()).isEqualTo(4);
        assertThat(payment.getOrderId()).isEqualTo(9);
        assertThat(payment.getAmount()).isEqualByComparingTo("99.98");
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethodEnum.PAYPAL);
        assertThat(payment.getCreateDate()).isNull();
        assertThat(payment.getLastModifiedDate()).isNull();
    }

    @Test
    void responsePreservesExistingCapitalizedIdAndTimestamps() {
        var created = LocalDateTime.of(2026, 11, 16, 15, 0);
        var modified = created.plusMinutes(1);
        var payment = Payment.builder().id(4).orderId(9).amount(BigDecimal.TEN)
                .paymentMethod(PaymentMethodEnum.PAYPAL).createDate(created).lastModifiedDate(modified).build();
        assertThat(mapper.fromPayment(payment)).isEqualTo(
                new PaymentResponse(4, 9, BigDecimal.TEN, PaymentMethodEnum.PAYPAL, created, modified));
    }

    @Test
    void preservesRejectionOfNullSources() {
        assertThatNullPointerException().isThrownBy(() -> mapper.toPayment(null));
        assertThatNullPointerException().isThrownBy(() -> mapper.fromPayment(null));
    }
}
