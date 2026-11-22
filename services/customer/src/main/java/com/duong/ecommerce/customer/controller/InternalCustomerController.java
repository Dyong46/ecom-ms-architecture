package com.duong.ecommerce.customer.controller;

import com.duong.module.web.payload.response.BaseResponse;

import com.duong.ecommerce.customer.payload.response.CustomerResponse;
import com.duong.ecommerce.customer.service.CustomerService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/customers")
@RequiredArgsConstructor
public class InternalCustomerController {
    private final CustomerService service;

    @GetMapping("/{customer-id}")
    public BaseResponse<CustomerResponse> findById(@PathVariable("customer-id") String customerId) {
        return BaseResponse.of(service.findById(customerId));
    }
}
