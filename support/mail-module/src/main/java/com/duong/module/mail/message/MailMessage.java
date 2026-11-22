package com.duong.module.mail.message;

import java.util.List;
import java.util.Objects;

/** Fully prepared content; template selection and business data belong to the caller. */
public record MailMessage(String senderAddress, List<String> receivers,
                          String subject, String content, boolean html) {
    public MailMessage {
        if (senderAddress == null || senderAddress.isBlank()) {
            throw new IllegalArgumentException("Mail sender address is required");
        }
        receivers = List.copyOf(Objects.requireNonNull(receivers, "receivers"));
        if (receivers.isEmpty() || receivers.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("At least one non-blank receiver is required");
        }
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(content, "content");
    }
}
