package com.duong.module.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class InternalServerErrorException extends BaseApiException {

	private static final long serialVersionUID = 2549990293476812305L;

	public InternalServerErrorException(String msg) {
		super(msg);
	}

}
