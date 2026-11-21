package com.duong.ecommerce.payment.exception;

import com.duong.module.common.exception.BaseApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class PaymentUnavailableException extends BaseApiException {
    public PaymentUnavailableException(String message) { super(message); }
}
