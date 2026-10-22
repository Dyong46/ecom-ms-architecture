package com.duong.module.web.payload.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import com.duong.module.web.dto.DebuggingDTO;

@Data
@Accessors(fluent = true, chain = true)
@NoArgsConstructor(staticName = "create")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorResponse {

  public ErrorResponse(String errorCode, Object extraData, DebuggingDTO debugInfo) {
    this.errorCode = errorCode;
    this.extraData = extraData;
    this.debugInfo = debugInfo;
  }

  @JsonProperty("error_code")
  private String errorCode;

  @JsonProperty("extra_data")
  private Object extraData;

  @JsonIgnore
  private DebuggingDTO debugInfo;

  /** Explicit opt-in for trusted diagnostic responses only; leave disabled in production. */
  @JsonProperty("debug_info")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public DebuggingDTO getSerializedDebugInfo() {
    return exposeDebugInfo ? debugInfo : null;
  }

  @JsonIgnore
  private transient boolean exposeDebugInfo;

  public ErrorResponse buildDebugInfo(Exception e) {
    this.debugInfo = DebuggingDTO.build(e);
    return this;
  }

}
