package com.duong.ecommerce.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

public interface ProductRepository extends JpaRepository<Product, Integer> {
    @Lock(PESSIMISTIC_WRITE)
    List<Product> findAllByIdInOrderById(List<Integer> productIds);
}
