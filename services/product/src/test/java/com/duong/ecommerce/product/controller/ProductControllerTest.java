package com.duong.ecommerce.product.controller;

import com.duong.ecommerce.product.payload.request.ProductPurchaseRequest;
import com.duong.ecommerce.product.service.ProductService;
import com.duong.module.web.exception.advice.ExceptionControllerAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductControllerTest {
    private ProductService service;
    private MockMvc mvc;
    private com.duong.ecommerce.product.service.ProductPurchaseService purchases;
    private static final String ID = "00000000-0000-4000-8000-000000000001";
    @BeforeEach void setup() {
        service=mock(ProductService.class);
        purchases = mock(com.duong.ecommerce.product.service.ProductPurchaseService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProductController(service, purchases))
                .setControllerAdvice(new ExceptionControllerAdvice()).build();
    }
    @Test void noContentDeletionDoesNotAcquireResponseBody() throws Exception {
        mvc.perform(delete("/api/v1/products/"+ID)).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).deleteProduct(ID);
    }
    @Test void acceptsSkuArrayAndRejectsOldProductIdContract() throws Exception {
        when(purchases.purchase(eq("test-key"), anyList())).thenReturn(List.of());
        mvc.perform(post("/api/v1/products/purchase").header("Idempotency-Key", "test-key").contentType(MediaType.APPLICATION_JSON)
                .content("[{\"skuId\":\""+ID+"\",\"quantity\":2}]"))
                .andExpect(status().isOk());
        verify(purchases).purchase("test-key", List.of(new ProductPurchaseRequest(ID, 2)));
        clearInvocations(service);
        mvc.perform(post("/api/v1/products/purchase").header("Idempotency-Key", "test-key").contentType(MediaType.APPLICATION_JSON)
                .content("[{\"productId\":1,\"quantity\":2}]"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void validatesSlugAndNestedVariants() throws Exception {
        mvc.perform(post("/api/v1/products").header("Idempotency-Key", "test-key").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Phone\",\"slug\":\"\",\"variants\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/products").header("Idempotency-Key", "test-key").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Phone\",\"slug\":\"phone\",\"variants\":[{\"sku\":\"X\",\"price\":-1,\"stockQuantity\":-1,\"optionValueIds\":[]}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
