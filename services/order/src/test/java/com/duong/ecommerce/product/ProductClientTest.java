package com.duong.ecommerce.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.duong.ecommerce.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class ProductClientTest {

    private OAuth2AuthorizedClientManager authorizedClientManager;
    private ProductClient productClient;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        var restTemplate = new RestTemplate();
        authorizedClientManager = mock(OAuth2AuthorizedClientManager.class);
        productClient = new ProductClient(restTemplate, authorizedClientManager);
        ReflectionTestUtils.setField(productClient, "productUrl", "http://product/api/v1/products");
        ReflectionTestUtils.setField(productClient, "productClientRegistrationId", "product-service");
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void purchaseAttachesClientCredentialsAccessToken() {
        var accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "service-token",
                Instant.now(), Instant.now().plusSeconds(300));
        var authorizedClient = mock(OAuth2AuthorizedClient.class);
        when(authorizedClient.getAccessToken()).thenReturn(accessToken);
        when(authorizedClientManager.authorize(any())).thenReturn(authorizedClient);
        server.expect(requestTo("http://product/api/v1/products/purchase"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(productClient.purchaseResponses(List.of(new PurchaseRequest(1, 2)))).isEmpty();
        server.verify();
    }

    @Test
    void purchaseStopsWhenClientCredentialsAuthorizationFails() {
        when(authorizedClientManager.authorize(any())).thenReturn(null);

        assertThatThrownBy(() -> productClient.purchaseResponses(List.of(new PurchaseRequest(1, 2))))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getMsg())
                        .isEqualTo("Unable to authorize product service request"));
    }
}
