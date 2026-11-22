package com.duong.ecommerce.payment.service;

import com.duong.ecommerce.payment.mapper.PaymentMapper;
import com.duong.ecommerce.payment.payload.request.PaymentRequest;
import com.duong.ecommerce.payment.payload.response.PaymentResponse;
import com.duong.ecommerce.payment.repository.PaymentRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository repository;
    private final PaymentMapper mapper;
    private final PaymentAttemptService attempts;
    private final PaymentSettlementService settlement;

    @org.springframework.transaction.annotation.Transactional
    public Integer createPayment(@NotNull @Valid PaymentRequest request) {
        long amount;
        try { amount=request.amount().longValueExact(); }
        catch (ArithmeticException exception) { throw new com.duong.module.common.exception.BadRequestException("VND amount must be a whole number within provider limits"); }
        if (amount<0) throw new com.duong.module.common.exception.BadRequestException("Amount cannot be negative");
        if (!java.util.Set.of(com.duong.ecommerce.payment.entity.enumeration.PaymentMethodEnum.CREDIT_CARD,
                com.duong.ecommerce.payment.entity.enumeration.PaymentMethodEnum.VISA,
                com.duong.ecommerce.payment.entity.enumeration.PaymentMethodEnum.MASTER_CARD).contains(request.paymentMethod())) {
            throw new com.duong.module.common.exception.BadRequestException("Only Stripe card payments are enabled");
        }
        var entity=mapper.toPayment(request);
        entity.initializeCheckout();
        var payment=repository.save(entity);
        attempts.create(payment.getId(),request.orderId(),amount);
        if(amount==0) settlement.succeed(payment.getId());
        return payment.getId();
    }

    public Page<PaymentResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::fromPayment);
    }
}
