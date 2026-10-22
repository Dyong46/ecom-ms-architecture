package com.duong.module.web.exception.handler;

import static com.duong.module.common.constant.ErrorCode.INVALID_INPUT_DATA;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.duong.module.web.payload.response.BaseResponse;
import com.duong.module.web.payload.response.ErrorResponse;

@Component
public class MethodArgumentNotValidExceptionHandler
		implements AbstractExceptionHandler<MethodArgumentNotValidException> {

	@Override
	public ResponseEntity<Object> handle(MethodArgumentNotValidException e, HttpServletRequest request) {
		BindingResult result = e.getBindingResult();
		ErrorResponse errorResponse = ErrorResponse.create().errorCode(INVALID_INPUT_DATA)
				.extraData(result.getAllErrors());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(BaseResponse.of(errorResponse));

	}

}
