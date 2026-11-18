package com.duong.ecommerce.product.repository;
import com.duong.ecommerce.product.entity.Option;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OptionRepository extends JpaRepository<Option, String> {
    boolean existsByName(String name);
}
