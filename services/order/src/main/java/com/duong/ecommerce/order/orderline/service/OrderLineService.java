package com.duong.ecommerce.order.orderline.service;

import com.duong.ecommerce.order.orderline.mapper.OrderLineMapper;
import com.duong.ecommerce.order.orderline.payload.request.OrderLineRequest;
import com.duong.ecommerce.order.orderline.payload.response.OrderLineResponse;
import com.duong.ecommerce.order.orderline.repository.OrderLineRepository;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class OrderLineService {

    private final OrderLineRepository repository;
    private final OrderLineMapper mapper;

    public Integer saveOrderLine(OrderLineRequest request) {
        var order = mapper.toOrderLine(request);
        return repository.save(order).getId();
    }

    public List<OrderLineResponse> findAllByOrderId(Integer orderId) {
        return repository.findAllByOrderId(orderId)
                .stream()
                .map(mapper::toOrderLineResponse)
                .collect(Collectors.toList());
    }
}
