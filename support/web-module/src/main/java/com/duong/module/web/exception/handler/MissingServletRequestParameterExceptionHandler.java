package com.duong.module.web.exception.handler;

import static com.duong.module.common.constant.ErrorCode.MISSING_PARAMETER;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MissingServletRequestParameterException;

import com.google.common.collect.ImmutableMap;

import com.duong.module.web.payload.response.BaseResponse;
import com.duong.module.web.payload.response.ErrorResponse;

@Component
public class MissingServletRequestParameterExceptionHandler
    implements AbstractExceptionHandler<MissingServletRequestParameterException> {

  @Override
  public ResponseEntity<Object> handle(MissingServletRequestParameterException e,
      HttpServletRequest request) {

    ErrorResponse errorResponse = ErrorResponse.create()
        .errorCode(MISSING_PARAMETER)
        .extraData(ImmutableMap.of(
            "parameter_name", e.getParameterName(),
            "parameter_type", e.getParameterType()
        ));

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(BaseResponse.of(errorResponse));

  }

}
