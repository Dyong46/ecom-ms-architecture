package com.duong.module.web.exception.handler;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.duong.module.common.exception.BaseApiException;
import com.duong.module.web.payload.response.BaseResponse;
import com.duong.module.web.payload.response.ErrorResponse;

@Component
public class BaseApiExceptionHandler implements AbstractExceptionHandler<BaseApiException> {

	@Override
	public ResponseEntity<Object> handle(BaseApiException e, HttpServletRequest request) {
		ErrorResponse errorResponse = ErrorResponse.create().errorCode(e.errorCode()).extraData(e.extraData())
				.buildDebugInfo(e);
		return ResponseEntity.status(getStatus(e)).body(BaseResponse.of(errorResponse));

	}

	private HttpStatus getStatus(BaseApiException e) {
		if (e.getClass().isAnnotationPresent(ResponseStatus.class)) {
			return e.getClass().getAnnotation(ResponseStatus.class).value();
		} else {
			return HttpStatus.INTERNAL_SERVER_ERROR;
		}
	}

}
