package com.duong.module.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends BaseApiException {

	private static final long serialVersionUID = 6403978041913515971L;

	public UnauthorizedException(String msg) {
		super(msg);
	}

}
