package com.duong.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "gateway.security")
public class GatewaySecurityProperties {

    private List<String> publicPaths = new ArrayList<>(List.of(
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info"
    ));
    private List<String> publicGetPaths = new ArrayList<>(List.of("/api/public/**"));
    private Callback callback = new Callback();

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public List<String> getPublicGetPaths() {
        return publicGetPaths;
    }

    public void setPublicGetPaths(List<String> publicGetPaths) {
        this.publicGetPaths = publicGetPaths;
    }

    public Callback getCallback() {
        return callback;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public static class Callback {
        private boolean enabled = true;
        private String pathPattern = "/api/callbacks/**";
        private List<String> allowedCidrs = new ArrayList<>(List.of("127.0.0.1/32", "::1/128"));
        private int trustedProxies;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPathPattern() {
            return pathPattern;
        }

        public void setPathPattern(String pathPattern) {
            this.pathPattern = pathPattern;
        }

        public List<String> getAllowedCidrs() {
            return allowedCidrs;
        }

        public void setAllowedCidrs(List<String> allowedCidrs) {
            this.allowedCidrs = allowedCidrs;
        }

        public int getTrustedProxies() {
            return trustedProxies;
        }

        public void setTrustedProxies(int trustedProxies) {
            this.trustedProxies = trustedProxies;
        }
    }
}
