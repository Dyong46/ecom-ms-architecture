package com.duong.module.kafka;

import com.duong.module.kafka.annotation.EnableKafkaModule;
import com.duong.module.kafka.service.KafkaPublisher;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(KafkaBrokerIntegrationTest.Config.class)
@ContextConfiguration(initializers = KafkaBrokerIntegrationTest.CommonConfig.class)
@TestPropertySource(properties = {
        "spring.kafka.consumer.group-id=kafka-module-fixture",
        "spring.kafka.producer.properties.spring.json.type.mapping=orderConfirmation:com.duong.module.kafka.KafkaBrokerIntegrationTest$ProducedOrder,paymentConfirmation:com.duong.module.kafka.KafkaBrokerIntegrationTest$ProducedPayment",
        "spring.kafka.consumer.properties.spring.json.type.mapping=orderConfirmation:com.duong.module.kafka.KafkaBrokerIntegrationTest$ConsumedOrder,paymentConfirmation:com.duong.module.kafka.KafkaBrokerIntegrationTest$ConsumedPayment",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=com.duong.module.kafka",
        "spring.kafka.admin.auto-create=true",
        "kafka-module.provision-topics=true",
        "kafka-module.topics[0].name=created-by-module",
        "kafka-module.topics[0].partitions=2",
        "kafka-module.topics[0].replicas=1"
})
@EmbeddedKafka(kraft = true, partitions = 1, topics = {"order-fixture", "payment-fixture"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers",
        brokerProperties = {"auto.create.topics.enable=false", "group.initial.rebalance.delay.ms=0"})
@DirtiesContext
class KafkaBrokerIntegrationTest {
    @Autowired KafkaPublisher publisher;
    @Autowired ListenerProbe listener;
    @Autowired EmbeddedKafkaBroker broker;

    @Test
    void publishesKeyedJsonAndConsumesBothAliasesUsingBootListenerFactory() throws Exception {
        var orderResult = publisher.send("order-fixture", "ORDER-1", new ProducedOrder("ORDER-1", 100))
                .get(15, TimeUnit.SECONDS);
        var paymentResult = publisher.send("payment-fixture", "ORDER-1", new ProducedPayment("ORDER-1", 100))
                .get(15, TimeUnit.SECONDS);
        assertThat(orderResult.getRecordMetadata().offset()).isGreaterThanOrEqualTo(0);
        assertThat(paymentResult.getRecordMetadata().offset()).isGreaterThanOrEqualTo(0);
        assertThat(listener.orders.poll(15, TimeUnit.SECONDS))
                .isEqualTo(new Received("ORDER-1", new ConsumedOrder("ORDER-1", 100)));
        assertThat(listener.payments.poll(15, TimeUnit.SECONDS))
                .isEqualTo(new Received("ORDER-1", new ConsumedPayment("ORDER-1", 100)));
    }

    @Test
    void provisionsDeclaredTopicThroughKafkaAdmin() throws Exception {
        try (var admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()))) {
            var topics = admin.describeTopics(List.of("created-by-module")).allTopicNames().get(15, TimeUnit.SECONDS);
            assertThat(topics.get("created-by-module").partitions()).hasSize(2);
        }
    }

    public record ProducedOrder(String orderReference, int amount) {}
    public record ProducedPayment(String orderReference, int amount) {}
    public record ConsumedOrder(String orderReference, int amount) {}
    public record ConsumedPayment(String orderReference, int amount) {}
    record Received(String key, Object event) {}

    static class ListenerProbe {
        final BlockingQueue<Received> orders = new LinkedBlockingQueue<>();
        final BlockingQueue<Received> payments = new LinkedBlockingQueue<>();

        @KafkaListener(topics = "order-fixture")
        void order(ConsumedOrder event, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
            orders.add(new Received(key, event));
        }

        @KafkaListener(topics = "payment-fixture")
        void payment(ConsumedPayment event, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
            payments.add(new Received(key, event));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableKafkaModule
    @ImportAutoConfiguration(KafkaAutoConfiguration.class)
    static class Config {
        @Bean
        ListenerProbe listenerProbe() {
            return new ListenerProbe();
        }
    }

    static class CommonConfig implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            KafkaModuleTest.commonConfig(context);
        }
    }
}
