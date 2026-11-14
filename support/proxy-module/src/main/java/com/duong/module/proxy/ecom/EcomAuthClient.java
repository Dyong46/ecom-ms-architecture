package com.duong.module.proxy.ecom;

import com.duong.module.proxy.config.ProxyFeignConfiguration;
import com.duong.module.proxy.ecom.config.EcomBasicAuthConfiguration;
import com.duong.module.proxy.ecom.response.EcomTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;

@FeignClient(name = "ecom-auth", url = "${integration-config.ecom.oauth-base-url}",
        configuration = {ProxyFeignConfiguration.class, EcomBasicAuthConfiguration.class})
public interface EcomAuthClient {
    @PostMapping(value = "/realms/{realm}/protocol/openid-connect/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    EcomTokenResponse oauthToken(@PathVariable("realm") String realm, @RequestBody Map<String, ?> form);
}
