package com.duong.module.proxy.payment;

import org.springframework.cloud.openfeign.FeignClient;
import com.duong.module.proxy.config.ProxyFeignConfiguration;
import com.duong.module.proxy.payment.request.PaymentRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "payment-service",
        url = "${application.config.payment-url}",
        configuration = {ProxyFeignConfiguration.class, PaymentOAuthConfiguration.class}
)
public interface PaymentClient {

    @PostMapping
    Integer requestOrderPayment(
            @RequestBody PaymentRequest paymentRequest
    );
}
