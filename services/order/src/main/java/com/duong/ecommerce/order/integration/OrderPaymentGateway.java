package com.duong.ecommerce.order.integration;

import com.duong.ecommerce.order.integration.oauth.EcomTokenService;
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

    public Integer requestOrderPayment(String key, PaymentRequest request) {
        String token = tokens.getAccessToken();
        var response = payments.requestOrderPayment("Bearer " + token, key, request);
        return response == null ? null : response.data();
    }
    public com.duong.module.proxy.payment.response.PaymentCheckoutResponse checkoutByOrder(int orderId) {
        var response=payments.checkoutByOrder("Bearer " + tokens.getAccessToken(),orderId);
        if(response==null || response.data()==null) throw new IllegalStateException("Payment returned no checkout state");
        return response.data();
    }
    public String retry(String key,int paymentId) {
        var response=payments.retry("Bearer " + tokens.getAccessToken(),key,paymentId);
        if(response==null || response.data()==null) throw new IllegalStateException("Payment returned no attempt");
        return response.data();
    }
}
