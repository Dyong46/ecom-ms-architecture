package com.duong.module.mail.config;

import com.duong.module.mail.service.SmtpMailService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration(proxyBeanMethods = false)
public class MailModuleConfig {
    @Bean
    SmtpMailService smtpMailService(JavaMailSender sender) {
        return new SmtpMailService(sender);
    }
}
