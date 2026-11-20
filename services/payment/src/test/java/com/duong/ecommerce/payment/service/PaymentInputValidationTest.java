package com.duong.ecommerce.payment.service;

import com.duong.ecommerce.payment.controller.InternalPaymentController;
import com.duong.ecommerce.payment.controller.PaymentController;
import com.duong.ecommerce.payment.entity.Payment;
import com.duong.ecommerce.payment.entity.enumeration.PaymentMethodEnum;
import com.duong.ecommerce.payment.integration.kafka.producer.NotificationProducer;
import com.duong.ecommerce.payment.mapper.PaymentMapper;
import com.duong.ecommerce.payment.payload.request.PaymentRequest;
import com.duong.ecommerce.payment.repository.PaymentRepository;
import com.duong.module.web.exception.advice.ExceptionControllerAdvice;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(PaymentInputValidationTest.Config.class)
class PaymentInputValidationTest {
    private static final String BODY = """
            {"amount":20.00,"paymentMethod":"PAYPAL","orderId":7,"orderReference":"ORDER-7",
             "customer":{"id":"c1","firstname":"Ada","lastname":"Lovelace","email":"ada@example.test"}}
            """;
    private final ObjectMapper json = new ObjectMapper();
    @Autowired
    PaymentService service;
    @Autowired
    PaymentCreationService creation;
    @Autowired
    PaymentRepository repository;
    @Autowired
    NotificationProducer producer;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        reset(repository, producer);
        mvc = MockMvcBuilders.standaloneSetup(new PaymentController(service, creation), new InternalPaymentController(creation))
                .setControllerAdvice(new ExceptionControllerAdvice()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/payments", "/internal/payments"})
    void invalidInputsAreRejectedBeforePersistenceOrPublication(String path) throws Exception {
        List<ObjectNode> invalid = new ArrayList<>();
        for (String field : List.of("amount", "paymentMethod", "orderId", "customer")) {
            var missing = body();
            missing.remove(field);
            invalid.add(missing);
            invalid.add(body().putNull(field));
        }
        invalid.add(body().put("amount", -1));
        invalid.add(body().put("amount", new BigDecimal("1.001")));
        invalid.add(body().put("amount", new BigDecimal("1" + "0".repeat(36))));
        invalid.add(body().put("orderId", 0));
        invalid.add(body().put("orderId", -1));
        invalid.add(body().put("paymentMethod", "UNKNOWN"));
        invalid.add(body().put("orderReference", "x".repeat(256)));
        for (String field : List.of("firstname", "lastname", "email")) {
            var blank = body();
            ((ObjectNode) blank.get("customer")).put(field, "  ");
            invalid.add(blank);
            var missing = body();
            ((ObjectNode) missing.get("customer")).remove(field);
            invalid.add(missing);
        }
        var badEmail = body();
        ((ObjectNode) badEmail.get("customer")).put("email", "invalid-email");
        invalid.add(badEmail);
        for (var payload : invalid) {
            mvc.perform(post(path).header("Idempotency-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(repository, producer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/payments", "/internal/payments"})
    void zeroPaymentIgnoresClientIdAndUsesGeneratedId(String path) throws Exception {
        when(repository.save(any())).thenAnswer(call -> {
            Payment payment = call.getArgument(0);
            return Payment.builder().id(42).amount(payment.getAmount())
                    .paymentMethod(payment.getPaymentMethod()).orderId(payment.getOrderId()).build();
        });
        when(producer.sendNotification(any())).thenReturn(CompletableFuture.completedFuture(null));
        var payload = body().put("id", 999).put("amount", BigDecimal.ZERO);
        mvc.perform(post(path).header("Idempotency-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(42));
        var saved = ArgumentCaptor.forClass(Payment.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getId()).isNull();
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(producer).sendNotification(any());
    }

    @Test
    void springServiceBoundaryRejectsMissingCustomerBeforeSaving() {
        assertThatThrownBy(() -> service.createPayment(new PaymentRequest(
                BigDecimal.TEN, PaymentMethodEnum.PAYPAL, 7, "ORDER-7", null)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.createPayment(null)).isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(repository, producer);
    }

    private ObjectNode body() throws Exception {
        return (ObjectNode) json.readTree(BODY);
    }

    @Configuration(proxyBeanMethods = false)
    static class Config {
        @Bean
        static MethodValidationPostProcessor methodValidation() {
            return new MethodValidationPostProcessor();
        }

        @Bean
        com.duong.ecommerce.payment.integration.idempotency.PostgresIdempotencyStore idempotency() {
            var store = mock(com.duong.ecommerce.payment.integration.idempotency.PostgresIdempotencyStore.class);
            doAnswer(call -> ((java.util.function.Function<String, Object>) call.getArgument(3)).apply("operation"))
                    .when(store).execute(anyString(), any(), any(), any());
            return store;
        }

        @Bean
        PaymentCreationService creation(PaymentService service,
                                        com.duong.ecommerce.payment.integration.idempotency.PostgresIdempotencyStore store) {
            return new PaymentCreationService(service, store);
        }

        @Bean
        PaymentRepository repository() {
            return mock(PaymentRepository.class);
        }

        @Bean
        NotificationProducer producer() {
            return mock(NotificationProducer.class);
        }

        @Bean
        PaymentService service(PaymentRepository repository, NotificationProducer producer) {
            return new PaymentService(repository, Mappers.getMapper(PaymentMapper.class), producer);
        }
    }
}
