package com.duong.ecommerce.payment.service;

import com.duong.ecommerce.payment.integration.kafka.producer.NotificationProducer;
import com.duong.ecommerce.payment.mapper.PaymentMapper;
import com.duong.ecommerce.payment.payload.event.PaymentNotificationRequest;
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
    private final NotificationProducer notificationProducer;

    public Integer createPayment(@NotNull @Valid PaymentRequest request) {
        var payment = repository.save(mapper.toPayment(request));

        var notification = new PaymentNotificationRequest(
                        request.orderReference(),
                        request.amount(),
                        request.paymentMethod(),
                        request.customer().firstname(),
                        request.customer().lastname(),
                        request.customer().email()
        );
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            notificationProducer.sendNotification(notification);
                        }
                    });
        } else {
            notificationProducer.sendNotification(notification);
        }
        return payment.getId();
    }

    public Page<PaymentResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::fromPayment);
    }
}
