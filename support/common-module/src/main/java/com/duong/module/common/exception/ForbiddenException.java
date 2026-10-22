package com.duong.module.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends BaseApiException {

	private static final long serialVersionUID = 3414254536846740226L;

	public ForbiddenException(String msg) {
		super(msg);
	}

}
