package com.duong.module.kafka;

import com.duong.module.kafka.annotation.EnableKafkaModule;
import com.duong.module.kafka.config.KafkaModuleProperties;
import com.duong.module.kafka.config.SafeProducerListener;
import com.duong.module.kafka.service.KafkaPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class KafkaModuleTest {
    static void commonConfig(org.springframework.context.ConfigurableApplicationContext ctx) {
        try {
            new YamlPropertySourceLoader().load("common", new ClassPathResource("config-fixtures/application.yml"))
                    .forEach(source -> ctx.getEnvironment().getPropertySources().addLast(source));
        } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
    }

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
                .withUserConfiguration(Enabled.class).withInitializer(KafkaModuleTest::commonConfig)
                .withPropertyValues("spring.kafka.admin.auto-create=false", "spring.kafka.listener.auto-startup=false");
    }

    @Test
    void optInReusesSingleBootProducerConsumerTemplateAndCentralJsonSettings() {
        context().run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(KafkaPublisher.class).hasSingleBean(KafkaTemplate.class)
                    .hasSingleBean(ProducerFactory.class).hasSingleBean(ConsumerFactory.class)
                    .hasSingleBean(SafeProducerListener.class);
            var producer = ctx.getBean(ProducerFactory.class).getConfigurationProperties();
            assertThat(producer.get("value.serializer")).isEqualTo(JsonSerializer.class);
            assertThat(producer.get("acks")).isEqualTo("all");
            assertThat(producer.get("enable.idempotence").toString()).isEqualTo("true");
            var consumer = ctx.getBean(ConsumerFactory.class).getConfigurationProperties();
            assertThat(consumer.get("value.deserializer")).isEqualTo(ErrorHandlingDeserializer.class);
            assertThat(consumer.get("enable.auto.commit")).isEqualTo(false);
        });
    }

    @Test
    void topicProvisioningIsExplicitAndDefinitionsAreValidated() {
        context().withPropertyValues("kafka-module.topics[0].name=fixture-topic")
                .run(ctx -> assertThat(ctx.getBean(KafkaModuleProperties.class).provisionTopics()).isFalse());
        context().withPropertyValues("kafka-module.provision-topics=true", "kafka-module.topics[0].name=fixture-topic",
                "kafka-module.topics[0].partitions=2", "kafka-module.topics[0].replicas=1")
                .run(ctx -> {
                    var config = ctx.getBean(KafkaModuleProperties.class);
                    assertThat(config.provisionTopics()).isTrue();
                    assertThat(config.topics().get(0).partitions()).isEqualTo(2);
                });
        for (String invalid : new String[]{"kafka-module.topics[0].name=bad name", "kafka-module.topics[0].partitions=0"}) {
            context().withPropertyValues("kafka-module.topics[0].name=fixture-topic", invalid)
                    .run(ctx -> assertThat(ctx).hasFailed());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void publisherReturnsBrokerFutureAndBothSyncAndAsyncFailuresRemainVisible() {
        KafkaTemplate<Object, Object> template = mock(KafkaTemplate.class);
        var publisher = new KafkaPublisher(template);
        var payload = new Event("ORDER-1", 20);
        var pending = new CompletableFuture<SendResult<Object, Object>>();
        when(template.send("order-topic", "ORDER-1", payload)).thenReturn(pending);
        assertThat(publisher.send("order-topic", "ORDER-1", payload)).isSameAs(pending);
        pending.completeExceptionally(new IllegalStateException("broker fixture failure"));
        assertThat(pending).isCompletedExceptionally();
        when(template.send("order-topic", null, payload)).thenThrow(new IllegalArgumentException("serialization fixture failure"));
        assertThat(publisher.send("order-topic", payload)).isCompletedExceptionally();
        assertThatThrownBy(() -> publisher.send("invalid topic", payload)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publisher.send("order-topic", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void withoutOptInBootInfrastructureExistsButModulePublisherDoesNot() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
                .withPropertyValues("spring.kafka.admin.auto-create=false")
                .run(ctx -> assertThat(ctx).hasSingleBean(KafkaTemplate.class).doesNotHaveBean(KafkaPublisher.class));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void asynchronousFailureLogsMetadataWithoutCustomerPayloadOrExceptionMessage(CapturedOutput output) {
        var record = new ProducerRecord<Object, Object>("order-topic", "private-fixture-key",
                new Event("private-fixture-reference", 20));
        new SafeProducerListener().onError(record, null, new IllegalStateException("private-fixture-exception"));
        assertThat(output.getAll()).contains("topic=order-topic")
                .doesNotContain("private-fixture-key", "private-fixture-reference", "private-fixture-exception");
    }

    public record Event(String reference, int amount) {}

    @Configuration(proxyBeanMethods = false)
    @EnableKafkaModule
    static class Enabled {}
}
