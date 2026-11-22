package com.duong.module.proxy.ecom.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EcomTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("token_type") String tokenType) {
    @Override
    public String toString() {
        return "EcomTokenResponse[accessToken=REDACTED, expiresIn=" + expiresIn + "]";
    }
}
