package com.duong.module.web.util;

import java.time.LocalDateTime;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class ServletUtils {

  public static HttpServletRequest getCurrentRequest() {
    RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    if (requestAttributes instanceof ServletRequestAttributes) {
      return ((ServletRequestAttributes) requestAttributes).getRequest();
    }
    return null;
  }

  public static <T> T getRequestAttribute(String name, Class<T> clazz) {
    return getRequestAttribute(name, clazz, null);
  }

  public static <T> T getRequestAttribute(String name, Class<T> clazz, T defaultValue) {
    HttpServletRequest request = getCurrentRequest();
    if (request != null) {
      Object object = request.getAttribute(name);
      if (object != null && clazz.isAssignableFrom(object.getClass())) {
        return clazz.cast(object);
      }
    }
    return defaultValue;
  }

  public static void setRequestAttribute(String name, Object object) {
    HttpServletRequest request = getCurrentRequest();
    if (request != null) {
      request.setAttribute(name, object);
    }
  }

  public static LocalDateTime getRequestDateTime() {
    return getRequestAttribute("now", LocalDateTime.class, LocalDateTime.now());
  }

  public static HttpServletResponse getCurrentResponse() {
    RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    if (requestAttributes instanceof ServletRequestAttributes) {
      return ((ServletRequestAttributes) requestAttributes).getResponse();
    }
    return null;
  }

}
