package com.duong.ecommerce.notification.service;

import com.duong.ecommerce.notification.repository.EmailJobStore;

import com.duong.ecommerce.notification.entity.Notification;
import com.duong.module.mail.message.MailMessage;
import com.duong.module.mail.service.SmtpMailService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "notification.delivery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EmailDeliveryWorker {
    private final EmailJobStore store;
    private final SmtpMailService smtp;
    private final SmtpFailurePolicy failures;
    private final Clock clock;
    private final Duration lease;
    private static final List<Duration> DELAYS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(15), Duration.ofHours(1));

    public EmailDeliveryWorker(EmailJobStore store, SmtpMailService smtp, SmtpFailurePolicy failures,
                               Clock clock, @Value("${notification.delivery.lease:PT2M}") Duration lease) {
        if (lease.isNegative() || lease.isZero()) throw new IllegalArgumentException("Lease must be positive");
        this.store = store; this.smtp = smtp; this.failures = failures; this.clock = clock; this.lease = lease;
    }

    @Scheduled(fixedDelayString = "${notification.delivery.poll-delay-ms:5000}")
    public void poll() {
        // Bound each polling cycle; multiple instances coordinate through atomic Mongo claims.
        for (int i = 0; i < 20; i++) {
            var job = store.claim(clock.instant(), lease);
            if (job == null) return;
            deliver(job);
        }
    }

    public void deliver(Notification job) {
        try {
            smtp.send(new MailMessage(job.getSenderAddress(), List.of(job.getRecipient()), job.getSubject(), job.getContent(), job.isHtml()));
        } catch (Exception exception) {
            var failure = failures.classify(exception);
            Instant retryAt = failure.retryable() && job.getAttemptCount() < EmailJobStore.MAX_ATTEMPTS
                    ? clock.instant().plus(retryDelay(job.getAttemptCount())) : null;
            store.markFailure(job, failure.code(), retryAt);
            log.warn("Email attempt failed job={} attempt={} code={}", job.getId(), job.getAttemptCount(), failure.code());
            return;
        }
        // Do not catch a Mongo completion failure as an SMTP failure or send again here.
        if (!store.markSent(job, clock.instant())) {
            log.warn("Email submitted but claim no longer owned job={}", job.getId());
        }
    }

    static Duration retryDelay(int attemptCount) {
        long millis = DELAYS.get(attemptCount - 1).toMillis();
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(millis * 80 / 100, millis * 120 / 100 + 1));
    }
}
