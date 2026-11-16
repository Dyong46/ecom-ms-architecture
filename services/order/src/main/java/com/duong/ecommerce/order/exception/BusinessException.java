package com.duong.ecommerce.order.exception;

import com.duong.module.common.exception.BadRequestException;

public class BusinessException extends BadRequestException {

    public BusinessException(String message) {
        super(message);
    }

    public String getMsg() {
        return getMessage();
    }
}
