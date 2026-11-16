package com.duong.ecommerce.customer.mapper;

import com.duong.ecommerce.customer.payload.request.CustomerRequest;
import com.duong.ecommerce.customer.payload.response.CustomerResponse;
import com.duong.ecommerce.customer.entity.Address;
import com.duong.ecommerce.customer.entity.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@SpringJUnitConfig(CustomerMapperImpl.class)
class CustomerMapperTest {

    @Autowired
    private CustomerMapper mapper;

    @Test
    void mapsRequestAndPreservesAddressReference() {
        var address = Address.builder().street("Main Street").houseNumber("12").zipCode("70000").build();
        var request = new CustomerRequest("c1", "Ada", "Lovelace", "ada@example.test", address);

        var customer = mapper.toCustomer(request);

        assertThat(customer).extracting(Customer::getId, Customer::getFirstname,
                        Customer::getLastname, Customer::getEmail)
                .containsExactly("c1", "Ada", "Lovelace", "ada@example.test");
        assertThat(customer.getAddress()).isSameAs(address);
    }

    @Test
    void mapsResponseAndPreservesAddressReference() {
        var address = Address.builder().street("Main Street").houseNumber("12").zipCode("70000").build();
        var customer = Customer.builder().id("c1").firstname("Ada").lastname("Lovelace")
                .email("ada@example.test").address(address).build();

        var response = mapper.fromCustomer(customer);

        assertThat(response).isEqualTo(new CustomerResponse("c1", "Ada", "Lovelace", "ada@example.test", address));
        assertThat(response.address()).isSameAs(address);
    }

    @Test
    void preservesNullFieldsWithoutCreatingAddress() {
        var customer = mapper.toCustomer(new CustomerRequest(null, null, null, null, null));

        assertThat(customer).extracting(Customer::getId, Customer::getFirstname,
                        Customer::getLastname, Customer::getEmail, Customer::getAddress)
                .containsOnlyNulls();
        assertThat(mapper.fromCustomer(customer)).isEqualTo(new CustomerResponse(null, null, null, null, null));
    }

    @Test
    void preservesExistingNullSourceBehavior() {
        assertThat(mapper.toCustomer(null)).isNull();
        assertThatNullPointerException().isThrownBy(() -> mapper.fromCustomer(null));
    }
}
