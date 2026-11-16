package com.duong.ecommerce.exception;

import com.duong.module.common.exception.BadRequestException;

public class ProductPurchaseException extends BadRequestException {

    public ProductPurchaseException(String message) {
        super(message);
    }
}
