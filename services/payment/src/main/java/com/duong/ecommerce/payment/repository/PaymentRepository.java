package com.duong.ecommerce.payment.repository;

import com.duong.ecommerce.payment.entity.Payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {
}
