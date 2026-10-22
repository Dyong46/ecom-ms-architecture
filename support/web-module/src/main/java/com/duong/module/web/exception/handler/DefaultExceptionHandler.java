package com.duong.module.web.exception.handler;

import static com.duong.module.common.constant.ErrorCode.UNKNOWN;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import com.duong.module.web.payload.response.BaseResponse;
import com.duong.module.web.payload.response.ErrorResponse;

@Slf4j
@Component
public class DefaultExceptionHandler implements AbstractExceptionHandler<Exception> {

  @Override
  public ResponseEntity<Object> handle(Exception e, HttpServletRequest request) {

    log.error("Handle undefined exception: ", e);

    ErrorResponse errorResponse = ErrorResponse.create()
        .errorCode(UNKNOWN)
        .buildDebugInfo(e);

    return ResponseEntity.status(getStatus(e, request))
        .body(BaseResponse.of(errorResponse));

  }

  private HttpStatus getStatus(Exception exception, HttpServletRequest request) {

    if (exception instanceof org.springframework.web.ErrorResponse errorResponse) {
      HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
      if (status != null) {
        return status;
      }
    }

    Integer statusCode = (Integer) request.getAttribute("jakarta.servlet.error.status_code");
    if (statusCode == null) {
      return HttpStatus.INTERNAL_SERVER_ERROR;
    } else {
      try {
        return HttpStatus.valueOf(statusCode);
      } catch (Exception e) {
        return HttpStatus.INTERNAL_SERVER_ERROR;
      }
    }

  }

}
