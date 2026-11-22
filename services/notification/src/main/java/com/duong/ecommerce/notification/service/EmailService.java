package com.duong.ecommerce.notification.service;

import com.duong.ecommerce.notification.entity.enumeration.EmailTemplatesEnum;

import com.duong.ecommerce.notification.payload.event.order.Product;
import com.duong.module.mail.message.MailMessage;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import static com.duong.ecommerce.notification.entity.enumeration.EmailTemplatesEnum.ORDER_CONFIRMATION;
import static com.duong.ecommerce.notification.entity.enumeration.EmailTemplatesEnum.PAYMENT_CONFIRMATION;

@Service
public class EmailService {
    private final SpringTemplateEngine templateEngine;
    private final String senderAddress;

    public EmailService(SpringTemplateEngine templateEngine,
                        @Value("${notification.email.sender-address}") String senderAddress) {
        this.templateEngine = templateEngine;
        this.senderAddress = senderAddress;
    }

    public MailMessage buildPaymentSuccessEmail(String destinationEmail, String customerName,
                                        BigDecimal amount, String orderReference) {
        return buildPaymentSuccessEmail(destinationEmail,customerName,amount,orderReference,null);
    }

    public MailMessage buildPaymentSuccessEmail(String destinationEmail,String customerName,
            BigDecimal amount,String orderReference,String currencyCode) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("currencyCode", currencyCode);
        variables.put("customerName", customerName);
        variables.put("amount", amount);
        variables.put("orderReference", orderReference);
        return build(PAYMENT_CONFIRMATION, variables, destinationEmail);
    }

    public MailMessage buildOrderConfirmationEmail(
        String destinationEmail,
        String customerName,
        BigDecimal amount,
        String orderReference,
        List<Product> products
    ) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("customerName", customerName);
        variables.put("totalAmount", amount);
        variables.put("orderReference", orderReference);
        variables.put("products", products);
        return build(ORDER_CONFIRMATION, variables, destinationEmail);
    }

    private MailMessage build(EmailTemplatesEnum template, Map<String, Object> variables, String destinationEmail) {
        var context = new Context();
        context.setVariables(variables);
        String html = templateEngine.process(template.getTemplate(), context);
        return new MailMessage(senderAddress, List.of(destinationEmail), template.getSubject(), html, true);
    }
}
