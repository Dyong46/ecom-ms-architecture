package com.duong.ecommerce.notification.delivery;

import com.duong.ecommerce.notification.Notification;
import com.duong.module.mail.service.SmtpMailService;
import java.net.SocketTimeoutException;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailDeliveryWorkerTest {
    final EmailJobStore store = mock(EmailJobStore.class);
    final SmtpMailService smtp = mock(SmtpMailService.class);
    final Instant now = Instant.parse("2026-11-16T00:00:00Z");
    final EmailDeliveryWorker worker = new EmailDeliveryWorker(store, smtp, new SmtpFailurePolicy(),
            Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(2));
    Notification job(int attempt) {
        return Notification.builder().id("event-1").attemptCount(attempt).leaseToken("claim-1")
                .senderAddress("shop@example.test").recipient("ada@example.test").subject("Subject")
                .content("Snapshot").html(true).build();
    }
    @Test void sentOnlyAfterSmtpAcceptance() throws Exception {
        var job = job(1); worker.deliver(job);
        var order = inOrder(smtp, store);
        order.verify(smtp).send(argThat(message -> message.content().equals("Snapshot")));
        order.verify(store).markSent(job, now);
        verify(store, never()).markFailure(any(), any(), any());
    }
    @Test void temporaryFailurePersistsDueTimeWithJitter() throws Exception {
        var job = job(1);
        doThrow(new MailSendException("Timeout", new SocketTimeoutException())).when(smtp).send(any());
        worker.deliver(job);
        var next = ArgumentCaptor.forClass(Instant.class);
        verify(store).markFailure(eq(job), eq("SMTP_CONNECTION_FAILURE"), next.capture());
        assertThat(next.getValue()).isBetween(now.plusSeconds(48), now.plusSeconds(72));
        verify(store, never()).markSent(any(), any());
        verify(smtp, times(1)).send(any());
    }
    @Test void fifthFailureIsTerminal() throws Exception {
        var job = job(5);
        doThrow(new MailSendException("Timeout", new SocketTimeoutException())).when(smtp).send(any());
        worker.deliver(job);
        verify(store).markFailure(job, "SMTP_CONNECTION_FAILURE", null);
    }
    @Test void authenticationFailureIsTerminal() throws Exception {
        var job = job(1);
        doThrow(new MailAuthenticationException("Fixture")).when(smtp).send(any());
        worker.deliver(job);
        verify(store).markFailure(job, "SMTP_AUTHENTICATION_FAILED", null);
    }
    @Test void dbCompletionFailureDoesNotCauseImmediateResend() throws Exception {
        var job = job(1);
        when(store.markSent(job, now)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("Mongo down"));
        assertThatThrownBy(() -> worker.deliver(job)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        verify(smtp, times(1)).send(any());
        verify(store, never()).markFailure(any(), any(), any());
    }
    @Test void jitterBoundsForEveryStage() {
        for (int attempt = 1; attempt <= 4; attempt++) {
            long seconds = new long[]{60,300,900,3600}[attempt-1];
            for (int i=0;i<50;i++) assertThat(EmailDeliveryWorker.retryDelay(attempt).toMillis())
                    .isBetween(seconds*800, seconds*1200);
        }
    }
}
