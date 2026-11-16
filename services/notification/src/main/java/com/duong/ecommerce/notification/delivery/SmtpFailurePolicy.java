package com.duong.ecommerce.notification.delivery;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Component;

@Component
public class SmtpFailurePolicy {
    public record Failure(boolean retryable, String code) {}

    public Failure classify(Throwable exception) {
        var failures = new java.util.ArrayList<Throwable>();
        collect(exception, Collections.newSetFromMap(new IdentityHashMap<>()), failures);
        // Permanent failures win over nested/secondary temporary failures.
        for (var failure : failures) {
            if (failure instanceof MailAuthenticationException || failure instanceof AuthenticationFailedException)
                return new Failure(false, "SMTP_AUTHENTICATION_FAILED");
            if (failure instanceof AddressException || failure instanceof MailParseException
                    || failure instanceof MailPreparationException || failure instanceof IllegalArgumentException
                    || failure instanceof javax.net.ssl.SSLHandshakeException)
                return new Failure(false, "INVALID_MAIL_MESSAGE");
            int code = smtpCode(failure);
            if (code >= 500 && code < 600) return new Failure(false, "SMTP_" + code);
        }
        for (var failure : failures) {
            int code = smtpCode(failure);
            if (code >= 400 && code < 500) return new Failure(true, "SMTP_" + code);
            if (failure instanceof IOException) return new Failure(true, "SMTP_CONNECTION_FAILURE");
        }
        return new Failure(false, "UNCLASSIFIED_MAIL_FAILURE");
    }

    private int smtpCode(Throwable failure) {
        if (failure instanceof SMTPAddressFailedException smtp) return smtp.getReturnCode();
        if (failure instanceof SMTPSendFailedException smtp) return smtp.getReturnCode();
        return 0;
    }

    private void collect(Throwable error, Set<Throwable> seen, java.util.List<Throwable> failures) {
        if (error == null || !seen.add(error)) return;
        failures.add(error);
        collect(error.getCause(), seen, failures);
        if (error instanceof MessagingException messaging) collect(messaging.getNextException(), seen, failures);
        if (error instanceof MailSendException mail) {
            for (var nested : mail.getMessageExceptions()) collect(nested, seen, failures);
        }
    }
}
