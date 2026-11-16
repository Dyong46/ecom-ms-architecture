package com.duong.ecommerce.notification.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.listener.MessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NotificationKafkaConfigurationTest {
    @Test
    void delaysIncreaseToOneMinuteAndStayCappedWithoutSleeping() {
        var backOff = NotificationKafkaConfiguration.ingestionBackOff();
        var execution = backOff.start();
        for (long delay : new long[]{1000, 2000, 4000, 8000, 16000, 32000, 60000}) {
            assertThat(execution.nextBackOff()).isEqualTo(delay);
        }
        for (int i = 0; i < 10_000; i++) {
            assertThat(execution.nextBackOff()).isEqualTo(60_000);
        }
        // A new retry sequence starts at one second, independent of the old one.
        assertThat(backOff.start().nextBackOff()).isEqualTo(1000);
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedIngestionSeeksWithoutCommitAndLogsMetadataWithoutPayloadOrStackTrace() {
        var handler = new NotificationKafkaConfiguration().notificationErrorHandler();
        Consumer<String, String> consumer = mock(Consumer.class);
        // A stopped container interrupts backoff promptly; no real broker or minute-long wait.
        var container = mock(MessageListenerContainer.class);
        var record = new ConsumerRecord<>("order-topic", 0, 3, "private-key", "private-payload");
        var logger = (Logger) LoggerFactory.getLogger(NotificationKafkaConfiguration.class);
        var logs = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        try {
            for (var failure : List.of(new IllegalStateException("private-error"),
                    new IllegalArgumentException("another-private-error"))) {
                assertThatThrownBy(() -> handler.handleRemaining(failure, List.of(record), consumer, container))
                        .isInstanceOf(KafkaException.class);
            }
            verify(consumer, times(2)).seek(new TopicPartition("order-topic", 0), 3);
            verifyNoMoreInteractions(consumer);
            assertThat(handler.isAckAfterHandle()).isFalse();
            assertThat(logs.list).hasSize(2).allSatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getFormattedMessage()).contains("topic=order-topic", "partition=0", "offset=3")
                        .doesNotContain("private-key", "private-payload", "private-error");
            });
            assertThat(logs.list.get(1).getFormattedMessage()).contains("attempt=2");
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }
}
