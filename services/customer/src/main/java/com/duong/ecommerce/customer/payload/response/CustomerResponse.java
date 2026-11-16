package com.duong.ecommerce.customer.payload.response;

import com.duong.ecommerce.customer.entity.Address;

public record CustomerResponse(
        String id,
        String firstname,
        String lastname,
        String email,
        Address address
) {
}
