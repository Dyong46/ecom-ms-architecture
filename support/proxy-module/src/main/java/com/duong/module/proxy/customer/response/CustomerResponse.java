package com.duong.module.proxy.customer.response;

public record CustomerResponse(
        String id,
        String firstname,
        String lastname,
        String email
) {
}
