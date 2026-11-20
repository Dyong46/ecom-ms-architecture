package com.duong.module.proxy.payment;

import com.duong.module.proxy.config.ProxyFeignConfiguration;
import com.duong.module.proxy.payment.request.PaymentRequest;
import com.duong.module.proxy.response.ServiceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "payment-service",
        url = "${application.config.payment-url}",
        configuration = ProxyFeignConfiguration.class
)
public interface PaymentClient {

    @PostMapping
    ServiceResponse<Integer> requestOrderPayment(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody PaymentRequest paymentRequest
    );
}
