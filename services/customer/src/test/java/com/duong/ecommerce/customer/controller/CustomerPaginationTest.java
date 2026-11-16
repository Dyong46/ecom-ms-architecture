package com.duong.ecommerce.customer.controller;

import com.duong.ecommerce.customer.payload.response.CustomerResponse;
import com.duong.ecommerce.customer.entity.Customer;
import com.duong.ecommerce.customer.mapper.CustomerMapper;
import com.duong.ecommerce.customer.repository.CustomerRepository;
import com.duong.ecommerce.customer.service.CustomerService;

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
class CustomerPaginationTest {
    @Test
    void createdCustomerIdUsesJsonEnvelopeInsteadOfRawString() throws Exception {
        when(service.createCustomer(org.mockito.ArgumentMatchers.any())).thenReturn("customer-1");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstname\":\"Ada\",\"lastname\":\"Lovelace\",\"email\":\"ada@example.test\",\"address\":{}}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data").value("customer-1"))
                .andExpect(jsonPath("$.request_id").hasJsonPath());
    }

    @Test
    void acceptedDeletionHasExplicitNullDataAndTraceId() throws Exception {
        org.slf4j.MDC.put("traceId", "customer-delete-trace");
        try {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/customers/c1"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$.request_id").value("customer-delete-trace"));
            verify(service).deleteCustomer("c1");
        } finally {
            org.slf4j.MDC.remove("traceId");
        }
    }

    @Mock private CustomerRepository repository;
    @Mock private CustomerMapper mapper;
    @InjectMocks private CustomerService actualService;

    private CustomerService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(CustomerService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CustomerController(service))
                .setControllerAdvice(new ExceptionControllerAdvice()).build();
    }

    @Test
    void defaultsToBoundedFirstPage() throws Exception {
        var pageable = PageRequest.of(0, 20, Sort.by("id").ascending());
        when(service.findAllCustomers(pageable)).thenReturn(Page.empty(pageable));

        mvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.request_id").hasJsonPath())
                .andExpect(jsonPath("$.data.request_id").doesNotExist())
                .andExpect(jsonPath("$.data.page_info.number").value(1))
                .andExpect(jsonPath("$.data.page_info.size").value(20))
                .andExpect(jsonPath("$.data.page_info.total_elements").value(0))
                .andExpect(jsonPath("$.data.page_info.total_pages").value(0))
                .andExpect(jsonPath("$.data.page_info.is_first").value(true))
                .andExpect(jsonPath("$.data.page_info.is_last").value(true));

        verify(service).findAllCustomers(pageable);
    }

    @Test
    void returnsRequestedPageAndMetadata() throws Exception {
        var pageable = PageRequest.of(1, 10, Sort.by("id").ascending());
        when(service.findAllCustomers(pageable)).thenReturn(new PageImpl<>(
                List.of(new CustomerResponse("customer-1", "Ada", "Lovelace", "ada@example.com", null)), pageable, 45));

        mvc.perform(get("/api/v1/customers").param("page", "2").param("size", "10"))
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
        when(service.findAllCustomers(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 45));

        mvc.perform(get("/api/v1/customers").param("page", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.page_info.total_elements").value(45))
                .andExpect(jsonPath("$.data.page_info.is_last").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=0", "page=-1", "size=0", "size=101", "page=abc", "size=abc"})
    void invalidParametersReturnProblemBeforeService(String query) throws Exception {
        var param = query.split("=");
        mvc.perform(get("/api/v1/customers").param(param[0], param[1]))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.extra_data.errors." + param[0]).exists());
        verifyNoInteractions(service);
    }

    @Test
    void repositoryQueryIsPagedAndMappingPreservesTotals() {
        var pageable = PageRequest.of(1, 10, Sort.by("id").ascending());
        var entity = mock(Customer.class);
        var response = new CustomerResponse("customer-1", "Ada", "Lovelace", "ada@example.com", null);
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(entity), pageable, 45));
        when(mapper.fromCustomer(entity)).thenReturn(response);

        var result = actualService.findAllCustomers(pageable);

        assertThat(result.getContent()).containsExactly(response);
        assertThat(result.getTotalElements()).isEqualTo(45);
        assertThat(result.getPageable()).isEqualTo(pageable);
        verify(repository).findAll(pageable);
        verify(repository, never()).findAll();
    }
}
