package com.duong.module.proxy.product;

import com.duong.module.proxy.response.ServiceResponse;

import com.duong.module.proxy.config.ProxyFeignConfiguration;
import com.duong.module.proxy.product.request.PurchaseRequest;
import com.duong.module.proxy.product.response.PurchaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "product-service", url = "${application.config.product-url}",
        configuration = ProxyFeignConfiguration.class)
public interface ProductClient {
    @PostMapping("/purchase")
    ServiceResponse<List<PurchaseResponse>> purchaseResponses(@RequestBody List<PurchaseRequest> request);
}
