package com.duong.gateway.security;

import com.duong.gateway.config.GatewaySecurityProperties;
import com.duong.gateway.handler.ProblemDetailsHandler;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class CallbackIpAllowlistWebFilter implements WebFilter {

    private final GatewaySecurityProperties properties;
    private final ProblemDetailsHandler problems;
    private final PathPattern callbackPath;
    private final List<CidrRange> allowedAddresses;

    public CallbackIpAllowlistWebFilter(
            GatewaySecurityProperties properties,
            ProblemDetailsHandler problems) {
        this.properties = properties;
        this.problems = problems;
        this.callbackPath = PathPatternParser.defaultInstance
                .parse(properties.getCallback().getPathPattern());
        this.allowedAddresses = properties.getCallback().getAllowedCidrs().stream()
                .map(CidrRange::parse)
                .toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.getCallback().isEnabled()
                || !callbackPath.matches(exchange.getRequest().getPath().pathWithinApplication())) {
            return chain.filter(exchange);
        }

        InetSocketAddress remoteAddress = resolveRemoteAddress(exchange);
        if (remoteAddress == null || remoteAddress.getAddress() == null || !isAllowed(remoteAddress)) {
            return problems.write(exchange, HttpStatus.FORBIDDEN);
        }

        return chain.filter(exchange);
    }

    private InetSocketAddress resolveRemoteAddress(ServerWebExchange exchange) {
        int trustedProxies = properties.getCallback().getTrustedProxies();
        if (trustedProxies > 0) {
            try {
                return XForwardedRemoteAddressResolver.maxTrustedIndex(trustedProxies).resolve(exchange);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return exchange.getRequest().getRemoteAddress();
    }

    private boolean isAllowed(InetSocketAddress remoteAddress) {
        return allowedAddresses.stream().anyMatch(range -> range.matches(remoteAddress.getAddress()));
    }

    private record CidrRange(byte[] network, int prefixLength) {

        private static CidrRange parse(String cidr) {
            String[] parts = cidr.split("/", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid callback CIDR: " + cidr);
            }
            try {
                byte[] network = InetAddress.getByName(parts[0]).getAddress();
                int prefixLength = Integer.parseInt(parts[1]);
                if (prefixLength < 0 || prefixLength > network.length * Byte.SIZE) {
                    throw new IllegalArgumentException("Invalid callback CIDR prefix: " + cidr);
                }
                return new CidrRange(network, prefixLength);
            } catch (UnknownHostException | NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid callback CIDR: " + cidr, exception);
            }
        }

        private boolean matches(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }

            int completeBytes = prefixLength / Byte.SIZE;
            int remainingBits = prefixLength % Byte.SIZE;
            for (int index = 0; index < completeBytes; index++) {
                if (candidate[index] != network[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }

            int mask = 0xFF << (Byte.SIZE - remainingBits);
            return (candidate[completeBytes] & mask) == (network[completeBytes] & mask);
        }
    }
}
