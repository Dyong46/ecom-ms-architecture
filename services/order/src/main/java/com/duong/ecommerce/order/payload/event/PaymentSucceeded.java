package com.duong.ecommerce.order.payload.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown=true)
public record PaymentSucceeded(String eventId,Integer paymentId,Integer orderId,String orderReference,
        BigDecimal amount,String currencyCode) { }
