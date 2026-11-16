package com.duong.ecommerce.notification.integration;

import com.duong.ecommerce.notification.repository.EmailJobStore;
import com.duong.ecommerce.notification.service.EmailDeliveryWorker;
import com.duong.ecommerce.notification.service.SmtpFailurePolicy;

import com.duong.ecommerce.notification.enumtype.DeliveryStatus;
import com.duong.ecommerce.notification.entity.Notification;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
class EmailJobStoreIntegrationTest {
    @Container static final GenericContainer<?> mongo = new GenericContainer<>("mongo:8.0.9").withExposedPorts(27017);
    private final Instant now = Instant.parse("2026-11-16T00:00:00Z");
    private MongoClient client;
    private MongoTemplate template;
    private EmailJobStore store;

    @BeforeEach
    void setup() {
        client = MongoClients.create("mongodb://" + mongo.getHost() + ":" + mongo.getMappedPort(27017)
                + "/?w=majority&journal=true&readConcernLevel=majority");
        template = new MongoTemplate(client, "test_" + UUID.randomUUID().toString().replace("-", ""));
        store = new EmailJobStore(template);
    }

    @AfterEach void close() { client.close(); }

    Notification job() {
        return Notification.builder().id("event-1").eventId("event-1").status(DeliveryStatus.PENDING)
                .senderAddress("shop@example.test").recipient("ada@example.test").subject("Subject")
                .content("Original content").html(true).nextRetryAt(now).build();
    }

    @Test
    void redeliveryDoesNotDuplicateOrReplaceOriginalJob() {
        store.enqueue(job());
        var duplicate = job(); duplicate.setContent("New content must not replace snapshot");
        store.enqueue(duplicate);
        assertThat(template.getCollection("notification").countDocuments()).isEqualTo(1);
        assertThat(template.findById("event-1", Notification.class).getContent()).isEqualTo("Original content");
    }

    @Test
    void packageMovePreservesLegacyMongoDiscriminatorsAndEmbeddedEvents() {
        var customer = new org.bson.Document("_class", "com.duong.ecommerce.kafka.order.Customer")
                .append("id", "customer-1").append("firstname", "Ada")
                .append("lastname", "Lovelace").append("email", "ada@example.test");
        var event = new org.bson.Document("_class", "com.duong.ecommerce.kafka.order.OrderConfirmation")
                .append("orderReference", "ORDER-1").append("customer", customer)
                .append("paymentMethod", "PAYPAL").append("products", java.util.List.of());
        var document = new org.bson.Document("_id", "legacy-event")
                .append("_class", "com.duong.ecommerce.notification.Notification")
                .append("eventId", "legacy-event").append("status", "PENDING")
                .append("attemptCount", 0).append("nextRetryAt", java.util.Date.from(now))
                .append("orderConfirmation", event);
        template.getCollection("notification").insertOne(document);

        var claimed = store.claim(now, Duration.ofMinutes(2));
        assertThat(claimed.getId()).isEqualTo("legacy-event");
        assertThat(claimed.getOrderConfirmation().customer().email()).isEqualTo("ada@example.test");
        assertThat(claimed.getOrderConfirmation().orderReference()).isEqualTo("ORDER-1");
        assertThat(template.getConverter().read(Object.class, document)).isInstanceOf(Notification.class);

        store.enqueue(job());
        assertThat(template.getCollection("notification").find(new org.bson.Document("_id", "event-1"))
                .first().getString("_class")).isEqualTo("com.duong.ecommerce.notification.Notification");
    }

    @Test
    void concurrentWorkersClaimOnlyOneCopy() throws Exception {
        store.enqueue(job());
        var pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(i -> (java.util.concurrent.Callable<Notification>) () -> store.claim(now, Duration.ofMinutes(2))).toList();
            long claims = 0;
            for (var result : pool.invokeAll(tasks)) if (result.get(10, TimeUnit.SECONDS) != null) claims++;
            assertThat(claims).isEqualTo(1);
            assertThat(template.findById("event-1", Notification.class).getAttemptCount()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void expiredLeaseCanBeReclaimedButOldWorkerCannotComplete() {
        store.enqueue(job());
        var first = store.claim(now, Duration.ofMinutes(2));
        assertThat(store.claim(now.plusSeconds(119), Duration.ofMinutes(2))).isNull();
        var second = new EmailJobStore(template).claim(now.plusSeconds(121), Duration.ofMinutes(2));
        assertThat(second.getAttemptCount()).isEqualTo(2);
        assertThat(second.getLeaseToken()).isNotEqualTo(first.getLeaseToken());
        assertThat(store.markSent(first, now.plusSeconds(122))).isFalse();
        assertThat(store.markFailure(first, "STALE", null)).isFalse();
        assertThat(store.markSent(second, now.plusSeconds(123))).isTrue();
        var saved = template.findById("event-1", Notification.class);
        assertThat(saved.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(saved.getSubmittedAt()).isEqualTo(now.plusSeconds(123));
        assertThat(saved.getNextRetryAt()).isNull();
        assertThat(store.claim(now.plusSeconds(1000), Duration.ofMinutes(2))).isNull();
    }

    @Test
    void retryIsDurableAndNotEligibleBeforeItsDueTime() {
        store.enqueue(job());
        var first = store.claim(now, Duration.ofMinutes(2));
        assertThat(store.markFailure(first, "SMTP_451", now.plusSeconds(60))).isTrue();
        var restarted = new EmailJobStore(template);
        assertThat(restarted.claim(now.plusSeconds(59), Duration.ofMinutes(2))).isNull();
        assertThat(restarted.claim(now.plusSeconds(60), Duration.ofMinutes(2)).getAttemptCount()).isEqualTo(2);
    }

    @Test
    void crashOnFifthAttemptBecomesFailedAfterLeaseExpires() {
        var job = job(); job.setAttemptCount(4); store.enqueue(job);
        assertThat(store.claim(now, Duration.ofMinutes(2)).getAttemptCount()).isEqualTo(5);
        assertThat(store.claim(now.plusSeconds(121), Duration.ofMinutes(2))).isNull();
        var saved = template.findById("event-1", Notification.class);
        assertThat(saved.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(saved.getLastError()).isEqualTo("LEASE_EXPIRED_AT_ATTEMPT_LIMIT");
        assertThat(saved.getAttemptCount()).isEqualTo(5);
    }

    @Test
    void legacyRecordsWithoutStatusAreNotAutomaticallyResent() {
        template.insert(Notification.builder().id("old-record").build());
        assertThat(store.claim(now, Duration.ofMinutes(2))).isNull();
    }

    @Test
    void workerResumesPersistedRetryWithNewInstanceAndThenMarksSent() throws Exception {
        var smtp = org.mockito.Mockito.mock(com.duong.module.mail.service.SmtpMailService.class);
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("Timeout", new java.net.SocketTimeoutException()))
                .doNothing().when(smtp).send(org.mockito.ArgumentMatchers.any());
        store.enqueue(job());
        new EmailDeliveryWorker(store, smtp, new SmtpFailurePolicy(),
                java.time.Clock.fixed(now, java.time.ZoneOffset.UTC), Duration.ofMinutes(2)).poll();
        var pending = template.findById("event-1", Notification.class);
        assertThat(pending.getStatus()).isEqualTo(DeliveryStatus.RETRY);
        assertThat(pending.getAttemptCount()).isEqualTo(1);
        assertThat(pending.getNextRetryAt()).isAfter(now);
        new EmailDeliveryWorker(new EmailJobStore(template), smtp, new SmtpFailurePolicy(),
                java.time.Clock.fixed(pending.getNextRetryAt(), java.time.ZoneOffset.UTC), Duration.ofMinutes(2)).poll();
        var sent = template.findById("event-1", Notification.class);
        assertThat(sent.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(sent.getAttemptCount()).isEqualTo(2);
        assertThat(sent.getSubmittedAt()).isEqualTo(pending.getNextRetryAt());
        org.mockito.Mockito.verify(smtp, org.mockito.Mockito.times(2)).send(org.mockito.ArgumentMatchers.any());
    }
}
