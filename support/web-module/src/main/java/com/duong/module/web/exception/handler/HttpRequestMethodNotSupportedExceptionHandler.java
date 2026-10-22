package com.duong.module.web.exception.handler;

import static com.duong.module.common.constant.ErrorCode.HTTP_REQUEST_METHOD_NOT_SUPPORTED;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import com.google.common.collect.ImmutableMap;

import com.duong.module.web.payload.response.BaseResponse;
import com.duong.module.web.payload.response.ErrorResponse;

@Component
public class HttpRequestMethodNotSupportedExceptionHandler
    implements AbstractExceptionHandler<HttpRequestMethodNotSupportedException> {

  @Override
  public ResponseEntity<Object> handle(HttpRequestMethodNotSupportedException e,
      HttpServletRequest request) {

    ErrorResponse errorResponse = ErrorResponse.create()
        .errorCode(HTTP_REQUEST_METHOD_NOT_SUPPORTED)
        .extraData(ImmutableMap.of(
            "method", e.getMethod(),
            "supported_methods",
            ObjectUtils.defaultIfNull(e.getSupportedMethods(), new String[]{})
        ))
        .buildDebugInfo(e);

    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(BaseResponse.of(errorResponse));

  }

}
