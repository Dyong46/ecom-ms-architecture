package com.duong.ecommerce.payment;

import com.duong.ecommerce.notification.NotificationProducer;
import com.duong.ecommerce.notification.PaymentNotificationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

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

    public List<PaymentResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(mapper::fromPayment)
                .collect(Collectors.toList());
    }
}
