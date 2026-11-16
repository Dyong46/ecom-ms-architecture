package com.duong.ecommerce.email;

import com.duong.ecommerce.kafka.order.Product;
import com.duong.module.mail.message.MailMessage;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.assertj.core.api.Assertions.*;

class EmailServiceTest {
    private EmailService service;

    @BeforeEach
    void setup() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        service = new EmailService(engine, "shop@example.test");
    }

    @Test
    void rendersPaymentTemplateBeforeCallingTransport() throws Exception {
        var message = service.buildPaymentSuccessEmail("ada@example.test", "Ada <Test>", new BigDecimal("20.00"), "ORDER-7");
        assertThat(message.senderAddress()).isEqualTo("shop@example.test");
        assertThat(message.receivers()).containsExactly("ada@example.test");
        assertThat(message.subject()).isEqualTo(EmailTemplates.PAYMENT_CONFIRMATION.getSubject());
        assertThat(message.html()).isTrue();
        assertThat(message.content()).contains("Ada &lt;Test&gt;", "20.00", "ORDER-7")
                .doesNotContain("${customerName}");
    }

    @Test
    void rendersOrderProductsInNotification() throws Exception {
        var message = service.buildOrderConfirmationEmail("ada@example.test", "Ada", new BigDecimal("20.00"), "ORDER-7",
                List.of(new Product(1, "Keyboard", "", new BigDecimal("10.00"), 2)));
        assertThat(message.subject()).isEqualTo(EmailTemplates.ORDER_CONFIRMATION.getSubject());
        assertThat(message.content()).contains("Keyboard", "ORDER-7", "20.00");
    }

}
