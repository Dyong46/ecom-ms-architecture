package com.duong.module.mail;

import com.duong.module.mail.annotation.EnableMailModule;
import com.duong.module.mail.service.SmtpMailService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MailModuleConfigurationTest {
    @Test
    void dependencyAloneDoesNotEnableMailModule() {
        new ApplicationContextRunner().run(context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(SmtpMailService.class));
    }

    @Test
    void explicitEnableRegistersTransportWithoutSendingMail() {
        var sender = mock(JavaMailSender.class);
        new ApplicationContextRunner().withBean(JavaMailSender.class, () -> sender)
                .withUserConfiguration(Enabled.class).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(SmtpMailService.class);
                    verifyNoInteractions(sender);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMailModule
    static class Enabled {}
}
