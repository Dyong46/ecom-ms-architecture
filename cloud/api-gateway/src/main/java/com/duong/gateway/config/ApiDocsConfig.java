package com.duong.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.Map;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@Configuration(proxyBeanMethods = false)
@Profile("local")
public class ApiDocsConfig {
    public static final String PATH = "/docs";
    private static final Map<String, String> SPECS = Map.of(
            "product", "openapi.yaml",
            "customer", "customer.yaml",
            "order", "order.yaml"
    );

    @Bean
    RouterFunction<ServerResponse> apiDocs(@Value("${api-docs.swagger-ui-version}") String version) {
        String webjar = "META-INF/resources/webjars/swagger-ui/" + version + "/";
        var html = new ClassPathResource("api-docs/index.html");
        var routes = RouterFunctions.route()
                .resource(GET(PATH + "/swagger-initializer.js"),
                        new ClassPathResource("api-docs/swagger-initializer.js"))
                .resource(GET(PATH + "/swagger-ui.css"), new ClassPathResource(webjar + "swagger-ui.css"))
                .resource(GET(PATH + "/swagger-ui-bundle.js"), new ClassPathResource(webjar + "swagger-ui-bundle.js"));
        SPECS.forEach((service, spec) -> {
            String servicePath = PATH + "/" + service;
            routes.resource(GET(servicePath), html)
                    .resource(GET(servicePath + "/openapi.yaml"), new ClassPathResource("api-docs/" + spec),
                            (resource, headers) -> headers.setContentType(MediaType.valueOf("application/yaml")));
        });
        return routes.build();
    }
}
