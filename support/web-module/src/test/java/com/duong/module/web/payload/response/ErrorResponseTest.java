package com.duong.module.web.payload.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ErrorResponseTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void diagnosticsAreHiddenByDefault() throws Exception {
    var response = ErrorResponse.create().errorCode("failure")
        .buildDebugInfo(new IllegalStateException("sensitive diagnostic"));
    var json = mapper.writeValueAsString(response);
    assertFalse(json.contains("debug_info"));
    assertFalse(json.contains("sensitive diagnostic"));
    assertFalse(json.contains("root_cause_message"));
  }

  @Test
  void diagnosticsRequireExplicitOptIn() {
    var response = ErrorResponse.create()
        .buildDebugInfo(new IllegalStateException("diagnostic"))
        .exposeDebugInfo(true);
    var json = mapper.valueToTree(response);
    assertTrue(json.get("debug_info").has("message"));
    assertTrue(json.get("debug_info").has("root_cause_message"));
    assertFalse(json.get("debug_info").has("stackTrace"));
  }
}
