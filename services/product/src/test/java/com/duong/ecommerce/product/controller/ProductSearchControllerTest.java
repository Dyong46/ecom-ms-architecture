package com.duong.ecommerce.product.controller;

import com.duong.ecommerce.product.integration.search.SkuSearchDocument;
import com.duong.ecommerce.product.payload.request.ProductSearchRequest;
import com.duong.ecommerce.product.service.ProductSearchService;
import com.duong.module.web.exception.advice.ExceptionControllerAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductSearchControllerTest {
    private ProductSearchService service;
    private MockMvc mvc;
    @BeforeEach void setUp() {
        service=mock(ProductSearchService.class);
        mvc=MockMvcBuilders.standaloneSetup(new ProductSearchController(service))
                .setControllerAdvice(new ExceptionControllerAdvice()).build();
    }
    @Test void bindsSkuFiltersAndReturnsRepresentativeSkuWithSpuPagination() throws Exception {
        when(service.search(any(),any())).thenReturn(new PageImpl<>(List.of(
                new SkuSearchDocument("sku-id","spu-id","PHONE-BLACK","phone","Phone",null,
                        BigDecimal.TEN,5,true,List.of())),PageRequest.of(1,5),6));
        mvc.perform(get("/api/v1/products/search").param("q","phone").param("page","2").param("size","5")
                        .param("options[0].name","Color").param("options[0].value","Black").param("hasStock","true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].skuId").value("sku-id"))
                .andExpect(jsonPath("$.data.content[0].spuId").value("spu-id"))
                .andExpect(jsonPath("$.data.page_info.number").value(2))
                .andExpect(jsonPath("$.data.page_info.total_elements").value(6));
        var captor=ArgumentCaptor.forClass(ProductSearchRequest.class);
        verify(service).search(captor.capture(),any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getOptions().get(0).getValue()).isEqualTo("Black");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getHasStock()).isTrue();
    }
    @Test void allowsBrowsingWithoutKeyword() throws Exception {
        when(service.search(any(),any())).thenReturn(Page.empty(PageRequest.of(0,20)));
        mvc.perform(get("/api/v1/products/search")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/search").param("q","   ")).andExpect(status().isOk());
    }
    @Test void rejectsMalformedFiltersOversizedKeywordAndInvalidPagination() throws Exception {
        mvc.perform(get("/api/v1/products/search").param("options[0].name","Color"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products/search").param("options[0].name"," ").param("options[0].value","Black"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products/search").param("q","x".repeat(201))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products/search").param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products/search").param("page","0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products/search").param("hasStock","invalid")).andExpect(status().isBadRequest());
        var tooMany=get("/api/v1/products/search");
        for(int i=0;i<21;i++) tooMany.param("options["+i+"].name","Color").param("options["+i+"].value","Black");
        mvc.perform(tooMany).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
