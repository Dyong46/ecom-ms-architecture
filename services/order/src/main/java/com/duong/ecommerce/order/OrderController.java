package com.duong.ecommerce.order;
import com.duong.ecommerce.pagination.PageResponse;
import com.duong.ecommerce.pagination.PaginationRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService service;

    @PostMapping
    public ResponseEntity<Integer> createOrder(
            @RequestBody @Valid OrderRequest request
    ) {
        return ResponseEntity.ok(service.createOrder(request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<OrderResponse>> findAll(
            @Valid @ModelAttribute PaginationRequest pagination
    ) {
        return ResponseEntity.ok(PageResponse.from(service.findAll(pagination.toPageable())));
    }

    @GetMapping("/{order-id}")
    public ResponseEntity<OrderResponse> findById(
            @PathVariable("order-id") Integer orderId
    ) {
        return ResponseEntity.ok(service.findById(orderId));
    }
}
