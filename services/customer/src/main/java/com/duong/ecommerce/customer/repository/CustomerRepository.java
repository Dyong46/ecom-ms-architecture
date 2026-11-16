package com.duong.ecommerce.customer.repository;

import com.duong.ecommerce.customer.entity.Customer;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface CustomerRepository extends MongoRepository<Customer, String> {
}
