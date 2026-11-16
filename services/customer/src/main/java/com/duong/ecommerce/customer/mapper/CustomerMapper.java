package com.duong.ecommerce.customer.mapper;

import com.duong.ecommerce.customer.payload.request.CustomerRequest;
import com.duong.ecommerce.customer.payload.response.CustomerResponse;
import com.duong.ecommerce.customer.entity.Customer;
import jakarta.validation.Valid;
import java.util.Objects;
import org.mapstruct.BeforeMapping;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CustomerMapper {

    Customer toCustomer(@Valid CustomerRequest request);

    CustomerResponse fromCustomer(Customer customer);

    @BeforeMapping
    default void requireCustomer(Customer customer) {
        // Preserve the manual mapper's rejection of a null response source.
        Objects.requireNonNull(customer, "customer");
    }
}
