package com.duong.module.web.payload.response;

import com.duong.module.web.exception.handler.DefaultExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultExceptionHandlerTest {
  private final DefaultExceptionHandler handler = new DefaultExceptionHandler();

  private HttpServletRequest request(Integer status) {
    return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
        new Class<?>[] {HttpServletRequest.class},
        (proxy, method, args) -> method.getName().equals("getAttribute") ? status : null);
  }

  @Test
  void exceptionStatusTakesPrecedenceOverServletAttribute() {
    assertEquals(HttpStatus.NOT_FOUND, handler.handle(
        new ResponseStatusException(HttpStatus.NOT_FOUND), request(500)).getStatusCode());
  }

  @Test
  void unresolvedExceptionStatusFallsBackToServletAttribute() {
    assertEquals(HttpStatus.BAD_REQUEST, handler.handle(
        new ResponseStatusException(HttpStatusCode.valueOf(599)), request(400)).getStatusCode());
  }

  @Test
  void ordinaryExceptionUsesServletStatusOrInternalServerError() {
    assertEquals(HttpStatus.FORBIDDEN, handler.handle(new Exception(), request(403)).getStatusCode());
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
        handler.handle(new Exception(), request(null)).getStatusCode());
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
        handler.handle(new Exception(), request(599)).getStatusCode());
  }
}
