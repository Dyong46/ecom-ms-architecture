package com.duong.module.common.util;

import org.slf4j.MDC;

public class LogUtils {

  public static String getTraceId() {
    return MDC.get("traceId");
  }

}
