package com.duong.module.web.exception.handler;

import static com.duong.module.common.constant.ErrorCode.INVALID_INPUT_DATA;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;

import com.duong.module.web.payload.response.BaseResponse;
import com.duong.module.web.payload.response.ErrorResponse;

@Component
public class HttpMessageConversionExceptionHandler implements
    AbstractExceptionHandler<HttpMessageNotReadableException> {

  @Override
  public ResponseEntity<Object> handle(HttpMessageNotReadableException e,
      HttpServletRequest request) {

    ErrorResponse errorResponse = ErrorResponse.create()
        .errorCode(INVALID_INPUT_DATA)
        .buildDebugInfo(e);

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(BaseResponse.of(errorResponse));

  }

}
