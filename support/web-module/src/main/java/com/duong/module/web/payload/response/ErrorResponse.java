package com.duong.module.web.payload.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import com.duong.module.web.dto.DebuggingDTO;

@Data
@Accessors(fluent = true, chain = true)
@NoArgsConstructor(staticName = "create")
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorResponse {

  @JsonProperty("error_code")
  private String errorCode;

  @JsonProperty("extra_data")
  private Object extraData;

  @JsonProperty("debug_info")
  private DebuggingDTO debugInfo;

  public ErrorResponse buildDebugInfo(Exception e) {
    this.debugInfo = DebuggingDTO.build(e);
    return this;
  }

}
