package com.duong.module.mail.service;

import com.duong.module.mail.message.MailMessage;
import jakarta.mail.MessagingException;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/** SMTP transport only. Does not schedule, retry, render templates or swallow failures. */
public class SmtpMailService {
    private final JavaMailSender sender;

    public SmtpMailService(JavaMailSender sender) {
        this.sender = sender;
    }

    public void send(MailMessage message) throws MessagingException {
        var mimeMessage = sender.createMimeMessage();
        var helper = new MimeMessageHelper(mimeMessage,
                MimeMessageHelper.MULTIPART_MODE_RELATED, StandardCharsets.UTF_8.name());
        helper.setValidateAddresses(true);
        helper.setFrom(message.senderAddress());
        helper.setTo(message.receivers().toArray(String[]::new));
        helper.setSubject(message.subject());
        helper.setText(message.content(), message.html());
        sender.send(mimeMessage);
    }
}
