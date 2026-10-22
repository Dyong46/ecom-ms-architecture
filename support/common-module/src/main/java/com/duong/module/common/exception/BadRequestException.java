package com.duong.module.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequestException extends BaseApiException {

	private static final long serialVersionUID = 587412655732882170L;

	public BadRequestException(String msg) {
		super(msg);
	}

}
