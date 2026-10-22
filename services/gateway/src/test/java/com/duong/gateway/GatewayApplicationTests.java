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
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.when;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@Import(GatewayApplicationTests.TestRoutes.class)
@SpringBootTest(properties = {
		"spring.config.import=optional:file:./config-not-required-for-tests.yml",
		"spring.cloud.config.enabled=false",
		"eureka.client.enabled=false"
})
class GatewayApplicationTests {

	@Autowired
	private ApplicationContext context;

	@MockBean
	private ReactiveJwtDecoder reactiveJwtDecoder;

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
		when(reactiveJwtDecoder.decode("invalid")).thenReturn(Mono.error(new BadJwtException("secret detail")));
		client().get().uri("/api/v1/products").headers(h -> h.setBearerAuth("invalid")).exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().valueEquals("WWW-Authenticate", "Bearer error=\"invalid_token\"")
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody().jsonPath("$.status").isEqualTo(401)
				.jsonPath("$.detail").isEqualTo("Unauthorized");
	}

	@Test
	void unknownRouteReturnsNotFoundProblem() {
		var jwt = Jwt.withTokenValue("valid").header("alg", "RS256").subject("customer").build();
		when(reactiveJwtDecoder.decode("valid")).thenReturn(Mono.just(jwt));
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
		var jwt = Jwt.withTokenValue("valid").header("alg", "RS256").subject("customer").build();
		when(reactiveJwtDecoder.decode("valid")).thenReturn(Mono.just(jwt));
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
	}

}
