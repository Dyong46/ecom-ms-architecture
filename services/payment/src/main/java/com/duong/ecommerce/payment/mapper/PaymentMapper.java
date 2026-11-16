package com.duong.ecommerce.payment.mapper;

import com.duong.ecommerce.payment.entity.Payment;
import com.duong.ecommerce.payment.payload.request.PaymentRequest;
import com.duong.ecommerce.payment.payload.response.PaymentResponse;

import jakarta.validation.Valid;
import java.util.Objects;
import org.mapstruct.BeforeMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PaymentMapper {
    @Mapping(target = "createDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    Payment toPayment(@Valid PaymentRequest request);

    @Mapping(target = "Id", source = "id")
    PaymentResponse fromPayment(Payment payment);

    @BeforeMapping
    default void requireRequest(PaymentRequest request) {
        Objects.requireNonNull(request, "request");
    }

    @BeforeMapping
    default void requirePayment(Payment payment) {
        Objects.requireNonNull(payment, "payment");
    }
}
