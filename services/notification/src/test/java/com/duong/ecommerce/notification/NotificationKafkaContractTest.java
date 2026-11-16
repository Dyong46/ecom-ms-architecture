package com.duong.ecommerce.notification;

import com.duong.ecommerce.notification.payload.event.order.OrderConfirmation;
import com.duong.ecommerce.notification.payload.event.payment.PaymentConfirmation;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.FileSystemResource;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.SerializationUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationKafkaContractTest {
    @Test
    void existingAliasesDeserializeToNotificationDtosUsingActualConfigServerSettings() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
                .withPropertyValues("spring.kafka.admin.auto-create=false")
                .withInitializer(context -> {
                    try {
                        var loader = new YamlPropertySourceLoader();
                        for (String file : new String[]{"notification-service.yml", "application.yml"}) {
                            var path = Path.of("../../cloud/config-server/src/main/resources/configurations", file);
                            loader.load(file, new FileSystemResource(path)).forEach(source ->
                                    context.getEnvironment().getPropertySources().addLast(source));
                        }
                    } catch (java.io.IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .run(context -> {
                    var properties = context.getBean(ConsumerFactory.class).getConfigurationProperties();
                    assertThat(properties.get("enable.auto.commit")).isEqualTo(false);
                    var factory = context.getBean("kafkaListenerContainerFactory", ConcurrentKafkaListenerContainerFactory.class);
                    assertThat(factory.getContainerProperties().getAckMode())
                            .isEqualTo(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
                    assertThat(factory.getContainerProperties().isSyncCommits()).isTrue();
                    assertThat(properties.get("group.id")).isEqualTo("paymentGroup,orderGroup");
                    assertThat(properties.get("spring.json.trusted.packages").toString()).doesNotContain("*");
                    var deserializer = new ErrorHandlingDeserializer<>();
                    deserializer.configure(properties, false);
                    try {
                        var orderHeaders = headers("orderConfirmation");
                        var order = deserializer.deserialize("order-topic", orderHeaders, """
                                {"eventId":"order-event-1","orderReference":"ORDER-1","totalAmount":100,"paymentMethod":"PAYPAL",
                                 "customer":{"id":"c1","firstname":"Ada","lastname":"Lovelace","email":"ada@example.test"},
                                 "products":[{"productId":1,"name":"Keyboard","description":"","price":50,"quantity":2}]}
                                """.getBytes(StandardCharsets.UTF_8));
                        assertThat(order).isInstanceOf(OrderConfirmation.class);
                        assertThat(((OrderConfirmation) order).eventId()).isEqualTo("order-event-1");
                        assertThat(((OrderConfirmation) order).orderReference()).isEqualTo("ORDER-1");
                        assertThat(orderHeaders.lastHeader(SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER)).isNull();
                        var payment = deserializer.deserialize("payment-topic", headers("paymentConfirmation"), """
                                {"eventId":"payment-event-1","orderReference":"ORDER-1","amount":100,"paymentMethod":"PAYPAL",
                                 "customerFirstname":"Ada","customerLastname":"Lovelace","customerEmail":"ada@example.test"}
                                """.getBytes(StandardCharsets.UTF_8));
                        assertThat(payment).isInstanceOf(PaymentConfirmation.class);
                        assertThat(((PaymentConfirmation) payment).eventId()).isEqualTo("payment-event-1");
                        assertThat(((PaymentConfirmation) payment).orderReference()).isEqualTo("ORDER-1");
                    } finally {
                        deserializer.close();
                    }
                });
    }

    private org.apache.kafka.common.header.Headers headers(String type) {
        return new RecordHeaders().add("__TypeId__", type.getBytes(StandardCharsets.UTF_8));
    }
}
