package com.duong.ecommerce.customer.controller;

import com.duong.module.web.payload.response.BaseResponse;

import com.duong.ecommerce.customer.payload.request.CustomerRequest;
import com.duong.ecommerce.customer.payload.response.CustomerResponse;
import com.duong.ecommerce.customer.service.CustomerService;
import com.duong.module.web.payload.response.PageResponse;
import com.duong.module.web.payload.filter.PaginationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService service;

    @PostMapping
    public ResponseEntity<BaseResponse<String>> createCustomer(
            @RequestBody @Valid CustomerRequest request
    ) {
        return ResponseEntity.ok(BaseResponse.of(service.createCustomer(request)));
    }

    @PutMapping
    public  ResponseEntity<BaseResponse<Void>> updateCustomer(
            @RequestBody @Valid CustomerRequest request
    ) {
        service.updateCustomer(request);
        return ResponseEntity.accepted().body(BaseResponse.of(null));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<CustomerResponse>>> findAll(
            @Valid @ModelAttribute PaginationRequest pagination
    ) {
        return ResponseEntity.ok(BaseResponse.of(PageResponse.build(service.findAllCustomers(pagination.toPageable()))));
    }

    @GetMapping("/exits/{customer-id}")
    public ResponseEntity<BaseResponse<Boolean>> exitsById(@PathVariable("customer-id") String customerId) {
        return ResponseEntity.ok(BaseResponse.of(service.exitsById(customerId)));
    }

    @GetMapping("/{customer-id}")
    public ResponseEntity<BaseResponse<CustomerResponse>> findById(@PathVariable("customer-id") String customerId) {
        return ResponseEntity.ok(BaseResponse.of(service.findById(customerId)));
    }

    @DeleteMapping("/{customer-id}")
    public  ResponseEntity<BaseResponse<Void>> delete(
            @PathVariable("customer-id") String customerId
    ) {
        service.deleteCustomer(customerId);
        return ResponseEntity.accepted().body(BaseResponse.of(null));
    }
}
