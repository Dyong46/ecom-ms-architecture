package com.duong.module.mail;

import com.duong.module.mail.message.MailMessage;
import com.duong.module.mail.service.SmtpMailService;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpMailServiceTest {
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final SmtpMailService service = new SmtpMailService(sender);
    private MimeMessage mime;

    @BeforeEach
    void setup() {
        mime = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mime);
    }

    @Test
    void createsUtf8HtmlWithExplicitSenderAndEveryReceiver() throws Exception {
        service.send(new MailMessage("shop@example.test", List.of("a@example.test", "b@example.test"),
                "Xác nhận đơn hàng", "<p>Đã thanh toán</p>", true));
        mime.saveChanges();
        assertThat(mime.getFrom()[0].toString()).isEqualTo("shop@example.test");
        assertThat(mime.getRecipients(Message.RecipientType.TO)).hasSize(2);
        assertThat(mime.getSubject()).isEqualTo("Xác nhận đơn hàng");
        var part = ((Multipart) mime.getContent()).getBodyPart(0);
        assertThat(part.isMimeType("text/html")).isTrue();
        assertThat(part.getContent().toString()).contains("Đã thanh toán");
        verify(sender).send(mime);
    }

    @Test
    void supportsPlainTextWithoutInterpretingItAsHtml() throws Exception {
        service.send(new MailMessage("shop@example.test", List.of("a@example.test"), "Subject", "Hello <Ada>", false));
        mime.saveChanges();
        var part = ((Multipart) mime.getContent()).getBodyPart(0);
        assertThat(part.isMimeType("text/plain")).isTrue();
        assertThat(part.getContent()).isEqualTo("Hello <Ada>");
    }

    @Test
    void propagatesTransportFailureWithoutRetrying() {
        var failure = new MailSendException("Test SMTP failure");
        doThrow(failure).when(sender).send(mime);
        assertThatThrownBy(() -> service.send(new MailMessage("shop@example.test", List.of("a@example.test"),
                "Subject", "Content", true))).isSameAs(failure);
        verify(sender, times(1)).send(mime);
    }

    @Test
    void rejectsInvalidRecipientBeforeSending() {
        assertThatThrownBy(() -> service.send(new MailMessage("shop@example.test", List.of("invalid@@example.test"),
                "Subject", "Content", true))).isInstanceOf(jakarta.mail.internet.AddressException.class);
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void messageCopiesReceiversAndRejectsEmptyLists() {
        var receivers = new ArrayList<>(List.of("a@example.test"));
        var message = new MailMessage("shop@example.test", receivers, "Subject", "Content", true);
        receivers.clear();
        assertThat(message.receivers()).containsExactly("a@example.test");
        assertThatIllegalArgumentException().isThrownBy(() ->
                new MailMessage("shop@example.test", List.of(), "Subject", "Content", true));
    }
}
