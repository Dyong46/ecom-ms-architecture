package com.duong.ecommerce.integration;

import com.duong.ecommerce.integration.oauth.EcomTokenService;
import com.duong.module.proxy.payment.PaymentClient;
import com.duong.module.proxy.payment.request.PaymentRequest;
import org.springframework.stereotype.Service;

/** Order's outbound policy: acquire a machine token before sending a payment request. */
@Service
public class OrderPaymentGateway {
    private final EcomTokenService tokens;
    private final PaymentClient payments;

    public OrderPaymentGateway(EcomTokenService tokens, PaymentClient payments) {
        this.tokens = tokens;
        this.payments = payments;
    }

    public Integer requestOrderPayment(PaymentRequest request) {
        String token = tokens.getAccessToken();
        var response = payments.requestOrderPayment("Bearer " + token, request);
        return response == null ? null : response.data();
    }
}
