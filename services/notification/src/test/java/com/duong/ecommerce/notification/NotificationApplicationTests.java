package com.duong.ecommerce.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.cloud.config.enabled=false",
        "notification.delivery.enabled=false",
		"eureka.client.enabled=false",
		"spring.kafka.listener.auto-startup=false",
		"spring.mail.host=localhost",
		"spring.mail.port=1025"
})
class NotificationApplicationTests {
    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.kafka.config.KafkaListenerEndpointRegistry registry;
    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.kafka.core.ConsumerFactory<?, ?> consumers;
    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.data.mongodb.core.MongoTemplate mongo;

    @Test
    void kafkaDoesNotAutoCommitOrAcknowledgeBeforeDurableStorage() {
        org.assertj.core.api.Assertions.assertThat(consumers.getConfigurationProperties()
                .get(org.apache.kafka.clients.consumer.ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)).isEqualTo(false);
        org.assertj.core.api.Assertions.assertThat(registry.getListenerContainers()).hasSize(2).allSatisfy(container -> {
            org.assertj.core.api.Assertions.assertThat(container.getContainerProperties().getAckMode())
                    .isEqualTo(org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE);
            org.assertj.core.api.Assertions.assertThat(container.getContainerProperties().isSyncCommits()).isTrue();
        });
    }

    @Test
    void notificationUsesMajorityJournaledWrites() {
        org.assertj.core.api.Assertions.assertThat(mongo.getDb().getWriteConcern().getWString()).isEqualTo("majority");
        org.assertj.core.api.Assertions.assertThat(mongo.getDb().getWriteConcern().getJournal()).isTrue();
        org.assertj.core.api.Assertions.assertThat(mongo.getDb().getReadConcern()).isEqualTo(com.mongodb.ReadConcern.MAJORITY);
    }

	@Test
	void contextLoads() {
	}

}
