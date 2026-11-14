package com.duong.module.proxy.payment;

import com.duong.module.proxy.ecom.EcomTokenService;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/** Registered only in the payment-service Feign child context. */
public class PaymentOAuthConfiguration {
    @Bean
    RequestInterceptor paymentBearerToken(EcomTokenService tokens) {
        return request -> {
            request.removeHeader("Authorization");
            request.header("Authorization", "Bearer " + tokens.getAccessToken());
        };
    }
}
