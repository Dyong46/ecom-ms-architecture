package com.duong.ecommerce.customer.mapper;

import com.duong.ecommerce.customer.entity.Customer;
import com.duong.ecommerce.customer.payload.request.CustomerRequest;
import com.duong.ecommerce.customer.payload.response.CustomerResponse;
import jakarta.validation.Valid;
import org.mapstruct.BeforeMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.Objects;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CustomerMapper {

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    Customer toCustomer(@Valid CustomerRequest request);

    CustomerResponse fromCustomer(Customer customer);

    @BeforeMapping
    default void requireCustomer(Customer customer) {
        // Preserve the manual mapper's rejection of a null response source.
        Objects.requireNonNull(customer, "customer");
    }
}
