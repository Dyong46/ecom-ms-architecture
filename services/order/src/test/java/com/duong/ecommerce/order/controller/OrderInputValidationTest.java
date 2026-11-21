package com.duong.ecommerce.order.controller;

import com.duong.ecommerce.order.service.OrderService;
import com.duong.module.web.exception.advice.ExceptionControllerAdvice;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderInputValidationTest {
    @Test
    void rejectsInvalidRecipientAndNestedAddressBeforeCheckout() throws Exception {
        var service = mock(OrderService.class);
        var checkout = mock(com.duong.ecommerce.order.service.OrderCheckoutService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new OrderController(service, checkout))
                .setControllerAdvice(new ExceptionControllerAdvice()).build();
        var json = new ObjectMapper();
        String body = """
                {"reference":"ORDER-1","paymentMethod":"CREDIT_CARD","customerId":"c1",
                 "products":[{"skuId":"00000000-0000-4000-8000-000000000001","quantity":1}],
                 "recipientFullName":"Other Recipient","recipientPhone":"+84901234567",
                 "shippingAddress":{"addressLine1":"12 Main","ward":"Ward","city":"City","countryCode":"VN"}}
                """;
        for (String field : new String[]{"recipientFullName", "recipientPhone", "shippingAddress"}) {
            var payload = (ObjectNode) json.readTree(body);
            payload.remove(field);
            mvc.perform(post("/api/v1/orders").header("Idempotency-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(payload.toString()))
                    .andExpect(status().isBadRequest());
        }
        for (String field : new String[]{"addressLine1", "ward", "city", "countryCode"}) {
            var payload = (ObjectNode) json.readTree(body);
            ((ObjectNode) payload.get("shippingAddress")).put(field, " ");
            mvc.perform(post("/api/v1/orders").header("Idempotency-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(payload.toString()))
                    .andExpect(status().isBadRequest());
        }
        for (String phone : new String[]{" ", "abc", "123", "9".repeat(33)}) {
            var payload = (ObjectNode) json.readTree(body);
            payload.put("recipientPhone", phone);
            mvc.perform(post("/api/v1/orders").header("Idempotency-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(payload.toString()))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service, checkout);
    }
}
