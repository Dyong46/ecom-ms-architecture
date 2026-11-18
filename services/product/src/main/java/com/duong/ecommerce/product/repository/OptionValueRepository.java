package com.duong.ecommerce.product.repository;
import com.duong.ecommerce.product.entity.OptionValue;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OptionValueRepository extends JpaRepository<OptionValue, String> {
    boolean existsByOptionIdAndValue(String optionId, String value);
}
