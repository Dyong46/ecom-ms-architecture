package com.duong.ecommerce.notification.service;

import jakarta.mail.internet.InternetAddress;
import java.net.ConnectException;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import static org.assertj.core.api.Assertions.*;

class SmtpFailurePolicyTest {
    final SmtpFailurePolicy policy = new SmtpFailurePolicy();
    @Test void nestedSmtp450RetriesAnd550IsPermanent() throws Exception {
        for (int code : new int[]{450,550}) {
            var nested = new SMTPAddressFailedException(new InternetAddress("ada@example.test"), "RCPT TO", code, "Fixture");
            var result = policy.classify(new MailSendException(java.util.Map.of("fixture",nested)));
            assertThat(result.retryable()).isEqualTo(code==450);
            assertThat(result.code()).isEqualTo("SMTP_"+code);
        }
    }
    @Test void connectionFailureRetriesButUnknownStops() {
        assertThat(policy.classify(new MailSendException("Connect",new ConnectException())).retryable()).isTrue();
        assertThat(policy.classify(new IllegalStateException("Unknown")).retryable()).isFalse();
    }
}
