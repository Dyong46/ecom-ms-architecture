package com.duong.module.web.exception.handler;

import static com.duong.module.common.constant.ErrorCode.INVALID_INPUT_DATA;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.duong.module.web.payload.response.BaseResponse;
import com.duong.module.web.payload.response.ErrorResponse;

@Component
public class MethodArgumentTypeMismatchExceptionHandler
    implements AbstractExceptionHandler<MethodArgumentTypeMismatchException> {

  @Override
  public ResponseEntity<Object> handle(MethodArgumentTypeMismatchException e,
      HttpServletRequest request) {

    ErrorResponse errorResponse = ErrorResponse.create()
        .errorCode(INVALID_INPUT_DATA)
        .buildDebugInfo(e);

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(BaseResponse.of(errorResponse));

  }

}
