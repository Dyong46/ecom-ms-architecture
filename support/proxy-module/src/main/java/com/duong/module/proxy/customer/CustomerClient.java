package com.duong.module.proxy.customer;

import org.springframework.cloud.openfeign.FeignClient;
import com.duong.module.proxy.config.ProxyFeignConfiguration;
import com.duong.module.proxy.customer.response.CustomerResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;

@FeignClient(
        name = "customer-service",
        url = "${application.config.customer-url}",
        configuration = ProxyFeignConfiguration.class
)
public interface CustomerClient {

    @GetMapping("/{customer-id}")
    Optional<CustomerResponse> findCustomerById(
            @PathVariable("customer-id") String customerId
    );
}
