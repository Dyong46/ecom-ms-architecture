
package com.duong.ecommerce.customer;
import com.duong.ecommerce.pagination.PageResponse;
import com.duong.ecommerce.pagination.PaginationRequest;
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
    public ResponseEntity<String> createCustomer(
            @RequestBody @Valid CustomerRequest request
    ) {
        return ResponseEntity.ok(service.createCustomer(request));
    }

    @PutMapping
    public  ResponseEntity<Void> updateCustomer(
            @RequestBody @Valid CustomerRequest request
    ) {
        service.updateCustomer(request);
        return ResponseEntity.accepted().build();
    }

    @GetMapping
    public ResponseEntity<PageResponse<CustomerResponse>> findAll(
            @Valid @ModelAttribute PaginationRequest pagination
    ) {
        return ResponseEntity.ok(PageResponse.from(service.findAllCustomers(pagination.toPageable())));
    }

    @GetMapping("/exits/{customer-id}")
    public ResponseEntity<Boolean> exitsById(@PathVariable("customer-id") String customerId) {
        return ResponseEntity.ok(service.exitsById(customerId));
    }

    @GetMapping("/{customer-id}")
    public ResponseEntity<CustomerResponse> findById(@PathVariable("customer-id") String customerId) {
        return ResponseEntity.ok(service.findById(customerId));
    }

    @DeleteMapping("/{customer-id}")
    public  ResponseEntity<Void> delete(
            @PathVariable("customer-id") String customerId
    ) {
        service.deleteCustomer(customerId);
        return ResponseEntity.accepted().build();
    }
}
