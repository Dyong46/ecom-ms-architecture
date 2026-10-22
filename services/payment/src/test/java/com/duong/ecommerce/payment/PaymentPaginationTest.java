package com.duong.ecommerce.payment;

import com.duong.ecommerce.handler.GlobalExceptionHandler;
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
class PaymentPaginationTest {
    @Mock private PaymentRepository repository;
    @Mock private PaymentMapper mapper;
    @InjectMocks private PaymentService actualService;

    private PaymentService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(PaymentService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PaymentController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void defaultsToBoundedFirstPage() throws Exception {
        var pageable = PageRequest.of(0, 20, Sort.by("id").ascending());
        when(service.findAll(pageable)).thenReturn(Page.empty(pageable));

        mvc.perform(get("/api/v1/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page_info.number").value(1))
                .andExpect(jsonPath("$.page_info.size").value(20))
                .andExpect(jsonPath("$.page_info.total_elements").value(0))
                .andExpect(jsonPath("$.page_info.total_pages").value(0))
                .andExpect(jsonPath("$.page_info.is_first").value(true))
                .andExpect(jsonPath("$.page_info.is_last").value(true));

        verify(service).findAll(pageable);
    }

    @Test
    void returnsRequestedPageAndMetadata() throws Exception {
        var pageable = PageRequest.of(1, 10, Sort.by("id").ascending());
        when(service.findAll(pageable)).thenReturn(new PageImpl<>(
                List.of(new PaymentResponse(1, 1, java.math.BigDecimal.TEN, PaymentMethod.CREDIT_CARD, null, null)), pageable, 45));

        mvc.perform(get("/api/v1/payments").param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page_info.number").value(2))
                .andExpect(jsonPath("$.page_info.size").value(10))
                .andExpect(jsonPath("$.page_info.total_elements").value(45))
                .andExpect(jsonPath("$.page_info.total_pages").value(5))
                .andExpect(jsonPath("$.page_info.is_first").value(false))
                .andExpect(jsonPath("$.page_info.is_last").value(false));
    }

    @Test
    void pageBeyondLastReturnsEmptyContent() throws Exception {
        var pageable = PageRequest.of(10, 20, Sort.by("id").ascending());
        when(service.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 45));

        mvc.perform(get("/api/v1/payments").param("page", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page_info.total_elements").value(45))
                .andExpect(jsonPath("$.page_info.is_last").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=0", "page=-1", "size=0", "size=101", "page=abc", "size=abc"})
    void invalidParametersReturnProblemBeforeService(String query) throws Exception {
        var param = query.split("=");
        mvc.perform(get("/api/v1/payments").param(param[0], param[1]))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors." + param[0]).exists());
        verifyNoInteractions(service);
    }

    @Test
    void repositoryQueryIsPagedAndMappingPreservesTotals() {
        var pageable = PageRequest.of(1, 10, Sort.by("id").ascending());
        var entity = mock(Payment.class);
        var response = new PaymentResponse(1, 1, java.math.BigDecimal.TEN, PaymentMethod.CREDIT_CARD, null, null);
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(entity), pageable, 45));
        when(mapper.fromPayment(entity)).thenReturn(response);

        var result = actualService.findAll(pageable);

        assertThat(result.getContent()).containsExactly(response);
        assertThat(result.getTotalElements()).isEqualTo(45);
        assertThat(result.getPageable()).isEqualTo(pageable);
        verify(repository).findAll(pageable);
        verify(repository, never()).findAll();
    }
}
