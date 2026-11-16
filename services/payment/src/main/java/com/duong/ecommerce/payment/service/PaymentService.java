package com.duong.ecommerce.payment.service;

import com.duong.ecommerce.payment.mapper.PaymentMapper;
import com.duong.ecommerce.payment.payload.request.PaymentRequest;
import com.duong.ecommerce.payment.payload.response.PaymentResponse;
import com.duong.ecommerce.payment.repository.PaymentRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.duong.ecommerce.payment.integration.kafka.producer.NotificationProducer;
import com.duong.ecommerce.payment.payload.event.PaymentNotificationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository repository;
    private final PaymentMapper mapper;
    private final NotificationProducer notificationProducer;

    public Integer createPayment(@Valid PaymentRequest request) {
        var payment = repository.save(mapper.toPayment(request));

        notificationProducer.sendNotification(
                new PaymentNotificationRequest(
                        request.orderReference(),
                        request.amount(),
                        request.paymentMethod(),
                        request.customer().firstname(),
                        request.customer().lastname(),
                        request.customer().email()
                )
        );

        return payment.getId();
    }

    public Page<PaymentResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::fromPayment);
    }
}
