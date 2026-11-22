package com.duong.ecommerce.order.integration.kafka;

import com.duong.ecommerce.order.entity.enumeration.PaymentMethodEnum;
import com.duong.ecommerce.order.payload.event.OrderConfirmation;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.kafka.support.serializer.JsonSerializer;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEventContractTest {
    @Test
    void configServerMappingKeepsStableTypeAliasAfterPackageMove() throws Exception {
        var resource = new FileSystemResource(Path.of(
                "../../cloud/config-server/src/main/resources/configurations/order-service.yml"));
        var mapping = new YamlPropertySourceLoader().load("order", resource).get(0)
                .getProperty("spring.kafka.producer.properties.spring.json.type.mapping");
        try (var serializer = new JsonSerializer<OrderConfirmation>()) {
            serializer.configure(Map.of(JsonSerializer.TYPE_MAPPINGS, mapping), false);
            var headers = new RecordHeaders();
            var event = new OrderConfirmation("ORDER-1", BigDecimal.TEN, PaymentMethodEnum.PAYPAL, null, List.of());
            serializer.serialize("order-topic", headers, event);
            assertThat(new String(headers.lastHeader("__TypeId__").value(), StandardCharsets.UTF_8))
                    .isEqualTo("orderConfirmation");
            assertThat(event.eventId()).isNotBlank();
        }
    }
}
