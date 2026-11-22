package com.duong.ecommerce.order.orderline.controller;

import com.duong.ecommerce.order.orderline.payload.response.OrderLineResponse;
import com.duong.ecommerce.order.orderline.service.OrderLineService;

import com.duong.module.web.payload.response.BaseResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/order-lines")
@RequiredArgsConstructor
public class OrderLineController {

    private final OrderLineService service;

    @GetMapping("/order/{order-id}")
    public ResponseEntity<BaseResponse<List<OrderLineResponse>>> findByOrderId(
            @PathVariable("order-id") Integer orderId
    ) {
        return ResponseEntity.ok(BaseResponse.of(service.findAllByOrderId(orderId)));
    }
}
