package com.duong.ecommerce.product.controller;
import com.duong.ecommerce.product.payload.request.*;
import com.duong.ecommerce.product.service.ProductService;
import com.duong.module.web.exception.advice.ExceptionControllerAdvice;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class ProductControllerTest {
    private ProductService service;
    private MockMvc mvc;
    private static final String ID = "00000000-0000-4000-8000-000000000001";
    @BeforeEach void setup() {
        service=mock(ProductService.class);
        mvc=MockMvcBuilders.standaloneSetup(new ProductController(service))
                .setControllerAdvice(new ExceptionControllerAdvice()).build();
    }
    @Test void noContentDeletionDoesNotAcquireResponseBody() throws Exception {
        mvc.perform(delete("/api/v1/products/"+ID)).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).deleteProduct(ID);
    }
    @Test void acceptsSkuArrayAndRejectsOldProductIdContract() throws Exception {
        when(service.purchaseProducts(anyList())).thenReturn(List.of());
        mvc.perform(post("/api/v1/products/purchase").contentType(MediaType.APPLICATION_JSON)
                .content("[{\"skuId\":\""+ID+"\",\"quantity\":2}]"))
                .andExpect(status().isOk());
        verify(service).purchaseProducts(List.of(new ProductPurchaseRequest(ID,2)));
        clearInvocations(service);
        mvc.perform(post("/api/v1/products/purchase").contentType(MediaType.APPLICATION_JSON)
                .content("[{\"productId\":1,\"quantity\":2}]"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void validatesSlugAndNestedVariants() throws Exception {
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Phone\",\"slug\":\"\",\"variants\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Phone\",\"slug\":\"phone\",\"variants\":[{\"sku\":\"X\",\"price\":-1,\"stockQuantity\":-1,\"optionValueIds\":[]}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
