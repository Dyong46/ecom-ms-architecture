package com.duong.module.kafka;

import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.*;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class KafkaWireContractTest {
    record Produced(String orderReference, int amount) {}
    record Consumed(String orderReference, int amount) {}

    @Test
    void typeAliasMapsProducerDtoToDifferentConsumerClassWithoutChangingJsonShape() {
        var serializer = new JsonSerializer<Produced>();
        serializer.configure(Map.of(JsonSerializer.TYPE_MAPPINGS, "orderConfirmation:" + Produced.class.getName()), false);
        var deserializer = new JsonDeserializer<Consumed>();
        deserializer.configure(Map.of(JsonDeserializer.TYPE_MAPPINGS, "orderConfirmation:" + Consumed.class.getName()), false);
        var headers = new RecordHeaders();
        var bytes = serializer.serialize("order-topic", headers, new Produced("ORDER-1", 20));
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("{\"orderReference\":\"ORDER-1\",\"amount\":20}");
        assertThat(new String(headers.lastHeader("__TypeId__").value(), StandardCharsets.UTF_8)).isEqualTo("orderConfirmation");
        assertThat(deserializer.deserialize("order-topic", headers, bytes)).isEqualTo(new Consumed("ORDER-1", 20));
        serializer.close();
        deserializer.close();
    }

    @Test
    void deserializationErrorsAreExposedInHeadersAndUntrustedTypeHeadersAreRejected() {
        var deserializer = new ErrorHandlingDeserializer<>(new JsonDeserializer<>());
        deserializer.configure(Map.of(JsonDeserializer.TRUSTED_PACKAGES, "com.duong.module.kafka"), false);
        var headers = new RecordHeaders().add("__TypeId__", "java.io.File".getBytes(StandardCharsets.UTF_8));
        assertThat(deserializer.deserialize("order-topic", headers, "\"/fixture\"".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(headers.lastHeader(SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER)).isNotNull();
        deserializer.close();
    }
}
