package com.duong.ecommerce.order.controller;

import com.duong.ecommerce.order.entity.Order;
import com.duong.ecommerce.order.entity.enumeration.PaymentMethodEnum;
import com.duong.ecommerce.order.mapper.OrderMapper;
import com.duong.ecommerce.order.payload.response.OrderResponse;
import com.duong.ecommerce.order.repository.OrderRepository;
import com.duong.ecommerce.order.service.OrderService;
import com.duong.module.web.exception.advice.ExceptionControllerAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OrderPaginationTest {
    @Mock private OrderRepository repository;
    @Mock private OrderMapper mapper;
    @InjectMocks private OrderService actualService;

    private OrderService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(OrderService.class);
        mvc = MockMvcBuilders.standaloneSetup(new OrderController(service, mock(com.duong.ecommerce.order.service.OrderCheckoutService.class)))
                .setControllerAdvice(new ExceptionControllerAdvice()).build();
    }

    @Test
    void defaultsToBoundedFirstPage() throws Exception {
        var pageable = PageRequest.of(0, 20, Sort.by("id").ascending());
        when(service.findAll(pageable)).thenReturn(Page.empty(pageable));

        mvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.page_info.number").value(1))
                .andExpect(jsonPath("$.data.page_info.size").value(20))
                .andExpect(jsonPath("$.data.page_info.total_elements").value(0))
                .andExpect(jsonPath("$.data.page_info.total_pages").value(0))
                .andExpect(jsonPath("$.data.page_info.is_first").value(true))
                .andExpect(jsonPath("$.data.page_info.is_last").value(true));

        verify(service).findAll(pageable);
    }

    @Test
    void returnsRequestedPageAndMetadata() throws Exception {
        var pageable = PageRequest.of(1, 10, Sort.by("id").ascending());
        when(service.findAll(pageable)).thenReturn(new PageImpl<>(
                List.of(new OrderResponse(1, "ORDER-1", java.math.BigDecimal.TEN, PaymentMethodEnum.CREDIT_CARD, "customer-1", null, null, null, null, null, null, null, null, null, null, null)), pageable, 45));

        mvc.perform(get("/api/v1/orders").param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.page_info.number").value(2))
                .andExpect(jsonPath("$.data.page_info.size").value(10))
                .andExpect(jsonPath("$.data.page_info.total_elements").value(45))
                .andExpect(jsonPath("$.data.page_info.total_pages").value(5))
                .andExpect(jsonPath("$.data.page_info.is_first").value(false))
                .andExpect(jsonPath("$.data.page_info.is_last").value(false));
    }

    @Test
    void pageBeyondLastReturnsEmptyContent() throws Exception {
        var pageable = PageRequest.of(10, 20, Sort.by("id").ascending());
        when(service.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 45));

        mvc.perform(get("/api/v1/orders").param("page", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.page_info.total_elements").value(45))
                .andExpect(jsonPath("$.data.page_info.is_last").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=0", "page=-1", "size=0", "size=101", "page=abc", "size=abc"})
    void invalidParametersReturnProblemBeforeService(String query) throws Exception {
        var param = query.split("=");
        mvc.perform(get("/api/v1/orders").param(param[0], param[1]))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.extra_data.errors." + param[0]).exists());
        verifyNoInteractions(service);
    }

    @Test
    void repositoryQueryIsPagedAndMappingPreservesTotals() {
        var pageable = PageRequest.of(1, 10, Sort.by("id").ascending());
        var entity = mock(Order.class);
        var response = new OrderResponse(1, "ORDER-1", java.math.BigDecimal.TEN, PaymentMethodEnum.CREDIT_CARD, "customer-1", null, null, null, null, null, null, null, null, null, null, null);
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(entity), pageable, 45));
        when(mapper.fromOrder(entity)).thenReturn(response);

        var result = actualService.findAll(pageable);

        assertThat(result.getContent()).containsExactly(response);
        assertThat(result.getTotalElements()).isEqualTo(45);
        assertThat(result.getPageable()).isEqualTo(pageable);
        verify(repository).findAll(pageable);
        verify(repository, never()).findAll();
    }
}
