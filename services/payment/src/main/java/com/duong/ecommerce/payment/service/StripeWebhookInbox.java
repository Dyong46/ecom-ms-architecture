package com.duong.ecommerce.payment.service;

import com.duong.ecommerce.payment.config.StripeSettings;
import com.duong.module.common.exception.BadRequestException;
import com.duong.module.common.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.net.Webhook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;

@Service
public class StripeWebhookInbox {
    private static final Set<String> SUPPORTED=Set.of("checkout.session.completed","checkout.session.expired",
            "checkout.session.async_payment_succeeded","checkout.session.async_payment_failed");
    private final StripeSettings settings;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PaymentAttemptService attempts;
    private final TransactionTemplate tx;
    public StripeWebhookInbox(StripeSettings settings,JdbcTemplate jdbc,ObjectMapper json,PaymentAttemptService attempts,
            PlatformTransactionManager manager) {
        this.settings=settings;this.jdbc=jdbc;this.json=json;this.attempts=attempts;
        tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Transactional
    public void receive(String raw,String signature) {
        try {
            if(!settings.enabled() || raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1_048_576
                    || !Webhook.Signature.verifyHeader(raw,signature,settings.webhookSecret(),300))
                throw new BadRequestException("Invalid Stripe webhook");
        } catch(Exception exception) { throw new BadRequestException("Invalid Stripe webhook signature"); }
        try {
            var event=json.readTree(raw);
            String id=event.path("id").asText();String type=event.path("type").asText();
            if(!id.matches("evt_[A-Za-z0-9_]{1,250}") || type.length()>100 || type.isBlank()
                    || event.path("livemode").asBoolean(true) || !"event".equals(event.path("object").asText()))
                throw new BadRequestException("Invalid sandbox webhook event");
            jdbc.update("insert into stripe_webhook_inbox(event_id,event_type,payload_json,status) values (?,?,?,?) on conflict do nothing",
                    id,type,raw,SUPPORTED.contains(type)?"PENDING":"IGNORED");
        } catch(com.fasterxml.jackson.core.JsonProcessingException exception) { throw new BadRequestException("Invalid webhook JSON"); }
    }
    public boolean processNext() {
        return Boolean.TRUE.equals(tx.execute(status -> {
            var rows=jdbc.query("select event_id,event_type,payload_json,retry_count from stripe_webhook_inbox "
                    + "where status='PENDING' and next_retry_at<=current_timestamp order by received_at limit 1 for update skip locked",
                    (row,index)->new Event(row.getString(1),row.getString(2),row.getString(3),row.getInt(4)));
            if(rows.isEmpty()) return false;
            var event=rows.get(0);
            try {
                var session=json.readTree(event.raw()).path("data").path("object");
                String attemptId=session.path("metadata").path("attempt_id").asText();
                if(attemptId.isBlank()) {
                    finish(event.id(),"IGNORED","NO_ATTEMPT_REFERENCE");return true;
                }
                attempts.applySession(attemptId,session);
                if("checkout.session.async_payment_failed".equals(event.type()) && !"paid".equals(session.path("payment_status").asText())) {
                    jdbc.update("update payment_attempt set status='FAILED',failure_code='STRIPE_ASYNC_FAILED' where id=? and status<>'SUCCEEDED'",attemptId);
                    jdbc.update("update payment set status='FAILED' where id=(select payment_id from payment_attempt where id=?) and status<>'SUCCEEDED' "
                            + "and ?=(select id from payment_attempt where payment_id=payment.id order by created_at desc,id desc limit 1)",attemptId,attemptId);
                }
                finish(event.id(),"PROCESSED",null);
            } catch(PaymentAttemptService.InvalidSessionException exception) { finish(event.id(),"REJECTED","SESSION_MISMATCH"); }
            catch(NotFoundException exception) {
                if(event.retries()>=49) finish(event.id(),"REJECTED","ATTEMPT_NOT_FOUND");
                else jdbc.update("update stripe_webhook_inbox set retry_count=retry_count+1,next_retry_at=?,failure_code='ATTEMPT_NOT_FOUND' where event_id=?",
                        Timestamp.from(Instant.now().plusSeconds(10)),event.id());
            } catch(com.fasterxml.jackson.core.JsonProcessingException exception) { finish(event.id(),"REJECTED","MALFORMED_EVENT"); }
            return true;
        }));
    }
    private void finish(String id,String state,String code) {
        jdbc.update("update stripe_webhook_inbox set status=?,failure_code=?,processed_at=current_timestamp where event_id=?",state,code,id);
    }
    private record Event(String id,String type,String raw,int retries) { }
}
