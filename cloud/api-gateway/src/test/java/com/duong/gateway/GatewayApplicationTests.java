package com.duong.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@Import(GatewayApplicationTests.TestRoutes.class)
@SpringBootTest(properties = {
		"spring.config.import=optional:file:./config-not-required-for-tests.yml",
		"spring.cloud.config.enabled=false",
		"eureka.client.enabled=false",
		"gateway.security.public-get-paths=/api/public/**",
		"gateway.security.callback.path-pattern=/api/callbacks/**",
		"gateway.security.callback.allowed-cidrs=10.0.0.0/8",
		"gateway.security.callback.trusted-proxies=1"
})
class GatewayApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
	}

	@Test
	void missingTokenReturnsUnauthorizedProblem() {
		client().get().uri("/api/v1/products").exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().valueEquals("WWW-Authenticate", "Bearer")
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.type").isEqualTo("about:blank")
				.jsonPath("$.title").isEqualTo("Unauthorized")
				.jsonPath("$.status").isEqualTo(401)
				.jsonPath("$.instance").isEqualTo("/api/v1/products");
	}

	@Test
	void invalidTokenReturnsProblemAndBearerChallenge() {
		client().get().uri("/api/v1/products").headers(h -> h.setBearerAuth("invalid")).exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().valueEquals("WWW-Authenticate", "Bearer error=\"invalid_token\"")
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody().jsonPath("$.status").isEqualTo(401)
				.jsonPath("$.detail").isEqualTo("Unauthorized");
	}

	@Test
	void publicGetDoesNotRequireToken() {
		client().get().uri("/api/public/test").exchange()
				.expectStatus().isOk()
				.expectBody(String.class).isEqualTo("public");
	}

	@Test
	void nonGetRequestToPublicPrefixStillRequiresToken() {
		client().post().uri("/api/public/test").exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void callbackFromAllowedForwardedAddressDoesNotRequireToken() {
		client().post().uri("/api/callbacks/test")
				.header("X-Forwarded-For", "10.20.30.40")
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class).isEqualTo("callback");
	}

	@Test
	void callbackFromAddressOutsideAllowlistIsForbidden() {
		client().post().uri("/api/callbacks/test")
				.header("X-Forwarded-For", "203.0.113.10")
				.exchange()
				.expectStatus().isForbidden()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(403);
	}

	@Test
	void unknownRouteReturnsNotFoundProblem() {
		client().get().uri("/unknown-route").headers(h -> h.setBearerAuth("valid")).exchange()
				.expectStatus().isNotFound()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody().jsonPath("$.status").isEqualTo(404)
				.jsonPath("$.instance").isEqualTo("/unknown-route");
	}

	private WebTestClient client() {
		return WebTestClient.bindToApplicationContext(context).build();
	}

	@Test
	void unavailableServiceReturns503Problem() {
		client().get().uri("/test-unavailable").headers(h -> h.setBearerAuth("valid")).exchange()
				.expectStatus().isEqualTo(503)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody().jsonPath("$.status").isEqualTo(503)
				.jsonPath("$.detail").isEqualTo("An unexpected error occurred")
				.jsonPath("$.instance").isEqualTo("/test-unavailable");
	}

	@TestConfiguration
	static class TestRoutes {
		@Bean
		RouteLocator testRoutes(RouteLocatorBuilder builder) {
			return builder.routes().route("unavailable", route -> route.path("/test-unavailable")
					.uri("lb://NONEXISTENT-SERVICE")).build();
		}

		@Bean
		RouterFunction<ServerResponse> testEndpoints() {
			return route(GET("/api/public/test"), request -> ServerResponse.ok().bodyValue("public"))
					.andRoute(POST("/api/public/test"), request -> ServerResponse.ok().bodyValue("protected"))
					.andRoute(POST("/api/callbacks/test"), request -> ServerResponse.ok().bodyValue("callback"));
		}

		@Bean
		ReactiveJwtDecoder testJwtDecoder() {
			return token -> {
				if (!"valid".equals(token)) {
					return Mono.error(new BadJwtException("secret detail"));
				}
				return Mono.just(Jwt.withTokenValue(token)
						.header("alg", "RS256")
						.subject("customer")
						.build());
			};
		}
	}

}
