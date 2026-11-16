package com.duong.module.proxy.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Wire contract for service responses, independent of the server's web library. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ServiceResponse<T>(T data, @JsonProperty("request_id") String requestId) {
}
