package com.duong.module.web;

import com.duong.module.common.exception.BadRequestException;
import com.duong.module.common.exception.ConflictException;
import com.duong.module.common.exception.InternalServerErrorException;
import com.duong.module.web.annotation.EnableWebModule;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.RequestDispatcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(SharedErrorHandlingTest.Config.class)
class SharedErrorHandlingTest {
    @Autowired WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void inheritedBaseExceptionStatusAndMessageArePreserved() throws Exception {
        mvc.perform(get("/probe/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.error_code").value("INVALID_INPUT_DATA"))
                .andExpect(jsonPath("$.data.extra_data.message").value("Purchase rejected"));
    }

    @Test
    void customCodeAndApprovedDetailsArePreservedWithTraceId() throws Exception {
        MDC.put("traceId", "test-trace");
        try {
            mvc.perform(get("/probe/custom"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.error_code").value("PRODUCT_OUT_OF_STOCK"))
                    .andExpect(jsonPath("$.data.extra_data.message").value("Insufficient stock"))
                    .andExpect(jsonPath("$.data.extra_data.details.available").value(2))
                    .andExpect(jsonPath("$.request_id").value("test-trace"));
        } finally {
            MDC.remove("traceId");
        }
    }

    @Test
    void missingEntityReturns404() throws Exception {
        mvc.perform(get("/probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.error_code").value("NOT_FOUND"));
    }

    @Test
    void conflictReturns409() throws Exception {
        mvc.perform(get("/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.error_code").value("CONFLICT"));
    }

    @Test
    void validationExposesOnlyMessagesAndFieldNames() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.extra_data.errors.name").value("Name is required"))
                .andExpect(jsonPath("$.data.extra_data.errors.password").exists())
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("rejectedValue"))));
    }

    @Test
    void malformedJsonDoesNotEchoInput() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{broken-secret"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.extra_data.message").value("Failed to read request"))
                .andExpect(content().string(not(containsString("broken-secret"))));
    }

    @Test
    void unsupportedMethodPreservesAllowHeader() throws Exception {
        mvc.perform(delete("/probe"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.data.error_code").value("HTTP_REQUEST_METHOD_NOT_SUPPORTED"));
    }

    @Test
    void unexpectedFailureIsSafe() throws Exception {
        mvc.perform(get("/probe/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.data.error_code").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.extra_data.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.data.debug_info").doesNotExist())
                .andExpect(content().string(not(containsString("internal-secret"))));
    }

    @Test
    void explicitServerExceptionAlsoSuppressesMessageAndDetails() throws Exception {
        mvc.perform(get("/probe/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.data.extra_data.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.data.extra_data.details").doesNotExist())
                .andExpect(content().string(not(containsString("internal-secret"))));
    }

    @Test
    void statusOnlyErrorDispatchHasEnvelope() throws Exception {
        mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.error_code").value("NOT_FOUND"));
    }

    @Test
    void unknownErrorDispatchIsSafe500() throws Exception {
        mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 999))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.data.extra_data.message").value("An unexpected error occurred"));
    }

    @Test
    void enablingErrorsDoesNotEnableWildcardCors() throws Exception {
        mvc.perform(options("/probe").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebModule
    static class Config {
        @Bean ProbeController probeController() { return new ProbeController(); }
    }

    record Payload(@NotBlank(message = "Name is required") String name, @Size(min = 8) String password) {}

    static class PurchaseRejectedException extends BadRequestException {
        PurchaseRejectedException() { super("Purchase rejected"); }
    }

    @RestController
    static class ProbeController {
        @PostMapping("/probe") String create(@RequestBody @Valid Payload payload) { return "ok"; }
        @GetMapping("/probe/bad-request") String badRequest() { throw new PurchaseRejectedException(); }
        @GetMapping("/probe/custom") String custom() {
            throw new BadRequestException("Insufficient stock")
                    .errorCode("PRODUCT_OUT_OF_STOCK").extraData("available", 2);
        }
        @GetMapping("/probe/missing") String missing() { throw new EntityNotFoundException("Product missing"); }
        @GetMapping("/probe/conflict") String conflict() { throw new ConflictException("Order already cancelled"); }
        @GetMapping("/probe/unexpected") String unexpected() { throw new IllegalStateException("internal-secret"); }
        @GetMapping("/probe/internal") String internal() {
            throw new InternalServerErrorException("internal-secret").extraData("token", "internal-secret");
        }
    }
}
