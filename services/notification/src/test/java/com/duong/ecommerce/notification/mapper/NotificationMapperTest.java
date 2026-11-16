package com.duong.ecommerce.notification.mapper;

import com.duong.ecommerce.notification.entity.Notification;
import com.duong.ecommerce.notification.entity.enumeration.DeliveryStatusEnum;
import com.duong.module.mail.message.MailMessage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.*;

@SpringJUnitConfig(NotificationMapperImpl.class)
class NotificationMapperTest {
    @Autowired NotificationMapper mapper;

    @Test
    void updatesEmailSnapshotWithoutChangingJobIdentityOrDeliveryState() {
        var due = Instant.parse("2026-11-16T00:00:00Z");
        var job = Notification.builder().id("event-1").eventId("event-1")
                .status(DeliveryStatusEnum.RETRY).attemptCount(2).nextRetryAt(due).leaseToken("lease-1").build();
        mapper.updateMailSnapshot(new MailMessage("shop@example.test", List.of("ada@example.test"),
                "Subject", "<p>Body</p>", true), job);
        assertThat(job.getSenderAddress()).isEqualTo("shop@example.test");
        assertThat(job.getRecipient()).isEqualTo("ada@example.test");
        assertThat(job.getSubject()).isEqualTo("Subject");
        assertThat(job.getContent()).isEqualTo("<p>Body</p>");
        assertThat(job.isHtml()).isTrue();
        assertThat(job.getId()).isEqualTo("event-1");
        assertThat(job.getEventId()).isEqualTo("event-1");
        assertThat(job.getStatus()).isEqualTo(DeliveryStatusEnum.RETRY);
        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThat(job.getNextRetryAt()).isEqualTo(due);
        assertThat(job.getLeaseToken()).isEqualTo("lease-1");
    }
}
