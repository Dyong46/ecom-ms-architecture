package com.duong.ecommerce.customer;

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
    public CustomerResponse findById(@PathVariable("customer-id") String customerId) {
        return service.findById(customerId);
    }
}
