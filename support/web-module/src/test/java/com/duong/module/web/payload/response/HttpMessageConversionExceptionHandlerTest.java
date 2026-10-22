package com.duong.module.web.payload.response;

import com.duong.module.web.exception.handler.DefaultExceptionHandler;
import com.duong.module.web.exception.handler.HttpMessageConversionExceptionHandler;
import com.duong.module.web.exception.handler.resolver.ExceptionHandlerResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class HttpMessageConversionExceptionHandlerTest {
  @Test
  void onlyReadFailuresAreClassifiedAsInvalidInput() {
    var inputHandler = new HttpMessageConversionExceptionHandler();
    var defaultHandler = new DefaultExceptionHandler();
    var resolver = new ExceptionHandlerResolver(defaultHandler,
        List.of(defaultHandler, inputHandler));
    var readFailure = new HttpMessageNotReadableException("invalid request", null, null);

    assertSame(inputHandler, resolver.resolveExceptionHandler(readFailure));
    assertEquals(HttpStatus.BAD_REQUEST, inputHandler.handle(readFailure, null).getStatusCode());
    assertSame(defaultHandler, resolver.resolveExceptionHandler(
        new HttpMessageNotWritableException("response write failure")));
  }
}
