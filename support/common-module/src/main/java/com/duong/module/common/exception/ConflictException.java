package com.duong.module.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends BaseApiException {

    public ConflictException(String message) {
        super(message);
    }
}
