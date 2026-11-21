package com.duong.module.proxy.payment;

import com.duong.module.proxy.config.ProxyFeignConfiguration;
import com.duong.module.proxy.payment.request.PaymentRequest;
import com.duong.module.proxy.response.ServiceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    @GetMapping("/order/{orderId}")
    ServiceResponse<com.duong.module.proxy.payment.response.PaymentCheckoutResponse> checkoutByOrder(
            @RequestHeader("Authorization") String authorization,@PathVariable int orderId);

    @PostMapping("/{paymentId}/attempts")
    ServiceResponse<String> retry(@RequestHeader("Authorization") String authorization,
            @RequestHeader("Idempotency-Key") String key,@PathVariable int paymentId);
}
