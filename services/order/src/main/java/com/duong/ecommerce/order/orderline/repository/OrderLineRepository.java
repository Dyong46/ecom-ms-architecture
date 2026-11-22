package com.duong.ecommerce.order.orderline.repository;

import com.duong.ecommerce.order.orderline.entity.OrderLine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderLineRepository extends JpaRepository<OrderLine, Integer> {
    List<OrderLine> findAllByOrderId(Integer orderId);
}
