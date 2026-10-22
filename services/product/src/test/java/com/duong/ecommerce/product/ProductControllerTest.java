package com.duong.ecommerce.product;

import com.duong.ecommerce.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductControllerTest {
    private ProductService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ProductService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProductController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsInvalidQuantityAtRequestBoundary(int quantity) throws Exception {
        mvc.perform(post("/api/v1/products/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"productId\":1,\"quantity\":" + quantity + "}]"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/v1/products/purchase"))
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(MethodArgumentNotValidException.class))
                .andExpect(jsonPath("$.errors['products[0].quantity']")
                        .value("Quantity is mandatory"));

        verifyNoInteractions(service);
    }

    @Test
    void acceptsExistingJsonArrayContract() throws Exception {
        var products = List.of(new ProductPurchaseRequest(1, 2));
        when(service.purchaseProducts(products)).thenReturn(List.of());

        mvc.perform(post("/api/v1/products/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"productId\":1,\"quantity\":2}]"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(service).purchaseProducts(products);
    }
}
