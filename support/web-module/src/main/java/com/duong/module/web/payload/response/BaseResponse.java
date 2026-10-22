package com.duong.module.web.payload.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.duong.module.common.util.LogUtils;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaseResponse<T> {

  public static final String SUCCESS = "SUCCESS";

  @JsonProperty("data")
  private T data;

  @JsonProperty("request_id")
  private String requestId;

  public static <T> BaseResponse<T> of(T data) {
    return BaseResponse.<T>builder()
        .data(data)
        .requestId(LogUtils.getTraceId())
        .build();
  }
}
