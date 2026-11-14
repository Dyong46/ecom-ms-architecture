package com.duong.ecommerce.product;

import com.duong.ecommerce.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {InternalProductController.class, ProductController.class},
        properties = "spring.cloud.config.enabled=false")
@Import(SecurityConfig.class)
class InternalProductControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ProductService service;
    @MockBean JwtDecoder jwtDecoder;

    @Test
    void internalPurchaseAcceptsArrayWithoutUserTokenOrCsrfAndCallsExistingService() throws Exception {
        when(service.purchaseProducts(anyList())).thenReturn(List.of());
        mvc.perform(post("/internal/products/purchase").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"productId\":1,\"quantity\":2}]"))
                .andExpect(status().isOk());
        verify(service).purchaseProducts(List.of(new ProductPurchaseRequest(1, 2)));
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void internalPurchaseStillValidatesQuantity() throws Exception {
        mvc.perform(post("/internal/products/purchase").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"productId\":1,\"quantity\":0}]"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void publicPurchaseIsNotOpenedByTheInternalRule() throws Exception {
        mvc.perform(post("/api/v1/products/purchase").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"productId\":1,\"quantity\":2}]"))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(service);
    }
}
