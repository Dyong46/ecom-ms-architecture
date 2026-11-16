package com.duong.ecommerce.payment.handler;

import com.duong.module.web.exception.advice.ExceptionControllerAdvice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SharedExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new ExceptionControllerAdvice()).build();
    }

    @Test
    void validationReturnsEnvelopeWithFieldErrors() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.extra_data.message").value("Request validation failed"))
                .andExpect(jsonPath("$.data.extra_data.errors.name").value("Name is required"));
    }

    @Test
    void malformedJsonReturnsEnvelopeWithoutLeakingInput() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{broken-secret"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.extra_data.message").value("Failed to read request"))
                .andExpect(jsonPath("$.request_id").hasJsonPath());
    }

    @Test
    void methodNotAllowedPreservesAllowHeader() throws Exception {
        mvc.perform(delete("/probe"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.error_code").isNotEmpty());
    }

    @Test
    void unexpectedFailureDoesNotLeakInternalDetails() throws Exception {
        mvc.perform(get("/probe/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.extra_data.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.request_id").hasJsonPath());
    }

    record Payload(@NotBlank(message = "Name is required") String name) {}

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {
        @PostMapping
        String create(@RequestBody @Valid Payload payload) { return "ok"; }
        @GetMapping("/unexpected")
        String unexpected() { throw new IllegalStateException("internal-secret"); }
    }
}
