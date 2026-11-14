package com.duong.ecommerce.customer;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InternalCustomerControllerTest {
    @Test
    void internalLookupReusesExistingServiceAndResponseContract() throws Exception {
        var service = mock(CustomerService.class);
        when(service.findById("c1")).thenReturn(new CustomerResponse("c1", "Ada", "Lovelace", "ada@example.test", null));
        var mvc = MockMvcBuilders.standaloneSetup(new InternalCustomerController(service)).build();
        mvc.perform(get("/internal/customers/c1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("c1"))
                .andExpect(jsonPath("$.email").value("ada@example.test"));
        verify(service).findById("c1");
    }
}
