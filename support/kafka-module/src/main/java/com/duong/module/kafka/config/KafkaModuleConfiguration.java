package com.duong.module.kafka.config;

import com.duong.module.kafka.service.KafkaPublisher;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.ProducerListener;

@Configuration(proxyBeanMethods = false)
@EnableKafka
@EnableConfigurationProperties(KafkaModuleProperties.class)
public class KafkaModuleConfiguration {
    @Bean
    @ConditionalOnMissingBean
    KafkaPublisher kafkaPublisher(KafkaTemplate<Object, Object> template) {
        return new KafkaPublisher(template);
    }

    @Bean
    @ConditionalOnMissingBean(ProducerListener.class)
    ProducerListener<Object, Object> kafkaProducerListener() {
        return new SafeProducerListener();
    }

    @Bean
    KafkaAdmin.NewTopics ecomKafkaTopics(KafkaModuleProperties properties) {
        if (!properties.provisionTopics()) {
            return new KafkaAdmin.NewTopics();
        }
        var topics = properties.topics().stream()
                .map(topic -> new NewTopic(topic.name(), topic.partitions(), topic.replicas()))
                .toArray(NewTopic[]::new);
        return new KafkaAdmin.NewTopics(topics);
    }
}
