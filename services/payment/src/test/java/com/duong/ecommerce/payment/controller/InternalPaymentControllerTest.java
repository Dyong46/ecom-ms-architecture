package com.duong.ecommerce.payment.controller;

import com.duong.ecommerce.payment.entity.enumeration.PaymentMethodEnum;
import com.duong.ecommerce.payment.payload.request.Customer;
import com.duong.ecommerce.payment.payload.request.PaymentRequest;
import com.duong.ecommerce.payment.service.PaymentService;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InternalPaymentControllerTest {
    @Test
    void internalPaymentAcceptsProxyWireContractAndReusesExistingService() throws Exception {
        var service = mock(PaymentService.class);
        var expected = new PaymentRequest(null, new BigDecimal("20.00"), PaymentMethodEnum.PAYPAL, 7, "ORDER-7",
                new Customer("c1", "Ada", "Lovelace", "ada@example.test"));
        when(service.createPayment(expected)).thenReturn(42);
        var mvc = MockMvcBuilders.standaloneSetup(new InternalPaymentController(service)).build();
        mvc.perform(post("/internal/payments").contentType(MediaType.APPLICATION_JSON).content("""
                {"amount":20.00,"paymentMethod":"PAYPAL","orderId":7,"orderReference":"ORDER-7",
                "customer":{"id":"c1","firstname":"Ada","lastname":"Lovelace","email":"ada@example.test"}}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(42));
        verify(service).createPayment(expected);
    }
}
