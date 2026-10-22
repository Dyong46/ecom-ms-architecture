package com.duong.module.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class DebuggingDTO {

  @JsonProperty("message")
  private String message;

  @JsonProperty("root_cause_message")
  private String rootCauseMessage;

//  @JsonProperty("stack_trace")
  @JsonIgnore
  private Collection<String> stackTrace;

  public static DebuggingDTO build(Throwable throwable) {
    try {
      if (throwable != null) {
        return DebuggingDTO.builder()
            .message(ExceptionUtils.getMessage(throwable))
            .rootCauseMessage(ExceptionUtils.getRootCauseMessage(throwable))
            .stackTrace(getStackTrace(throwable))
            .build();
      }
    } catch (Exception e) {
      log.error("Error when build DebuggingDTO", e);
    }
    return new DebuggingDTO();
  }

  private static Collection<String> getStackTrace(Throwable throwable) {
    Set<String> stackTrace = new LinkedHashSet<>();
    if (throwable != null) {
      stackTrace.addAll(getStackTrace(throwable.getCause()));
      stackTrace.addAll(Arrays.stream(throwable.getStackTrace())
          .filter(stackTraceElement -> stackTraceElement.getClassName().contains("com.duong"))
          .map(stackTraceElement -> String.format("%s.%s(%d)",
              stackTraceElement.getClassName(),
              stackTraceElement.getMethodName(),
              stackTraceElement.getLineNumber()))
          .collect(Collectors.toList()));
    }
    return stackTrace;
  }

}
