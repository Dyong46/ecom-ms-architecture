package com.duong.ecommerce.payment.service.worker;

import com.duong.ecommerce.payment.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name="payment.workers.enabled",havingValue="true")
public class PaymentWorkers {
    private final PaymentAttemptService attempts;
    private final StripeWebhookInbox inbox;
    private final PaymentOutboxPublisher outbox;
    @Scheduled(fixedDelayString="${payment.workers.poll-delay-ms:3000}")
    public void inbox() { run("webhook",inbox::processNext,10); }
    @Scheduled(fixedDelayString="${payment.workers.poll-delay-ms:3000}")
    public void outbox() { run("outbox",outbox::processNext,2); }
    @Scheduled(fixedDelayString="${payment.workers.poll-delay-ms:3000}")
    public void attempts() { run("stripe",attempts::processNext,2); }
    private void run(String kind,java.util.function.BooleanSupplier next,int limit) {
        try { for(int count=0;count<limit && next.getAsBoolean();count++) { } }
        catch(RuntimeException exception) { log.warn("Payment worker failed: kind={}, errorType={}",kind,exception.getClass().getSimpleName()); }
    }
}
