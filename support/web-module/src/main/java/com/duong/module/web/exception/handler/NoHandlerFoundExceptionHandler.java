//package com.duong.module.web.exception.handler;
//
//import static com.duong.module.common.constant.ErrorCode.NO_HANDLER_FOUND;
//
//import jakarta.servlet.http.HttpServletRequest;
//
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.stereotype.Component;
//import org.springframework.web.servlet.NoHandlerFoundException;
//
//import com.google.common.collect.ImmutableMap;
//
//import com.duong.module.web.payload.response.BaseResponse;
//import com.duong.module.web.payload.response.ErrorResponse;
//
//@Component
//public class NoHandlerFoundExceptionHandler implements
//    AbstractExceptionHandler<NoHandlerFoundException> {
//
//  @Override
//  public ResponseEntity<Object> handle(NoHandlerFoundException e, HttpServletRequest request) {
//
//    ErrorResponse errorResponse = ErrorResponse.create()
//        .errorCode(NO_HANDLER_FOUND)
//        .extraData(ImmutableMap.of("request_url", e.getRequestURL()))
//        .buildDebugInfo(e);
//
//    return ResponseEntity.status(HttpStatus.NOT_FOUND)
//        .body(BaseResponse.of(errorResponse));
//
//  }
//
//}
