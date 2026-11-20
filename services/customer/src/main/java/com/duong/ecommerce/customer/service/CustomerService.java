package com.duong.ecommerce.customer.service;

import com.duong.ecommerce.customer.entity.Customer;
import com.duong.ecommerce.customer.mapper.CustomerMapper;
import com.duong.ecommerce.customer.payload.request.CustomerRequest;
import com.duong.ecommerce.customer.payload.response.CustomerResponse;
import com.duong.ecommerce.customer.repository.CustomerRepository;
import com.duong.module.common.exception.ConflictException;
import com.duong.module.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import static java.lang.String.format;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository repository;
    private final CustomerMapper mapper;

    public String createCustomer(CustomerRequest request) {
        var customer = saveCustomer(mapper.toCustomer(request));
        return customer.getId();
    }

    public void updateCustomer(CustomerRequest request) {
        var customer = repository.findById(request.id()).orElseThrow(() -> new NotFoundException(
                format("Cannot update customer:: No customer found with the provided ID:: %s", request.id())
        ));

        mergerCustomer(customer, request);
        saveCustomer(customer);
    }

    private Customer saveCustomer(Customer customer) {
        try {
            return repository.save(customer);
        } catch (DuplicateKeyException exception) {
            // Do not expose MongoDB's duplicate-key payload (which includes customer data).
            throw new ConflictException("Customer conflicts with an existing record");
        }
    }

    private void mergerCustomer(Customer customer, CustomerRequest request) {
         if (StringUtils.isNotBlank(request.firstname())) {
             customer.setFirstname(request.firstname());
         }

         if (StringUtils.isNotBlank(request.lastname())) {
             customer.setLastname(request.lastname());
         }

         if (StringUtils.isNotBlank(request.email())) {
             customer.setEmail(request.email());
         }

         if (request.address() != null) {
             customer.setAddress(request.address());
         }
    }

    public Page<CustomerResponse> findAllCustomers(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::fromCustomer);
    }

    public Boolean exitsById(String customerId) {
        return repository.findById(customerId)
                .isPresent();
    }

    public CustomerResponse findById(String customerId) {
        return repository.findById(customerId)
                .map(mapper::fromCustomer)
                .orElseThrow(() -> new NotFoundException(format("Not customer found with the provided ID:: %s", customerId)));
    }

    public void deleteCustomer(String customerId) {
        repository.deleteById(customerId);
    }
}
