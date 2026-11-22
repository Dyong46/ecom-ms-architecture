package com.duong.ecommerce.order.repository;

import com.duong.ecommerce.order.entity.Order;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Integer> {
}
