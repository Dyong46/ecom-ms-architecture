package com.duong.ecommerce.payment.integration.kafka;

import com.duong.ecommerce.payment.enumtype.PaymentMethod;
import com.duong.ecommerce.payment.payload.event.PaymentNotificationRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.kafka.support.serializer.JsonSerializer;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEventContractTest {
    @Test
    void configServerMappingKeepsStableTypeAliasAfterPackageMove() throws Exception {
        var resource = new FileSystemResource(Path.of(
                "../../cloud/config-server/src/main/resources/configurations/payment-service.yml"));
        var mapping = new YamlPropertySourceLoader().load("payment", resource).get(0)
                .getProperty("spring.kafka.producer.properties.spring.json.type.mapping");
        try (var serializer = new JsonSerializer<PaymentNotificationRequest>()) {
            serializer.configure(Map.of(JsonSerializer.TYPE_MAPPINGS, mapping), false);
            var headers = new RecordHeaders();
            var event = new PaymentNotificationRequest("ORDER-1", BigDecimal.TEN, PaymentMethod.PAYPAL,
                    "Ada", "Lovelace", "ada@example.test");
            serializer.serialize("payment-topic", headers, event);
            assertThat(new String(headers.lastHeader("__TypeId__").value(), StandardCharsets.UTF_8))
                    .isEqualTo("paymentConfirmation");
            assertThat(event.eventId()).isNotBlank();
        }
    }
}
