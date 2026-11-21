package com.duong.ecommerce.payment.service;

import com.duong.ecommerce.payment.payload.event.PaymentNotificationRequest;
import com.duong.module.kafka.service.KafkaPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class PaymentOutboxPublisher {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final KafkaPublisher kafka;
    private final TransactionTemplate tx;
    public PaymentOutboxPublisher(JdbcTemplate jdbc,ObjectMapper json,KafkaPublisher kafka,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.json=json;this.kafka=kafka;
        tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public boolean processNext() {
        var item=tx.execute(status -> {
            var rows=jdbc.query("select event_id,topic,payload_json,retry_count from payment_outbox where published_at is null "
                    + "and next_retry_at<=current_timestamp and (lease_until is null or lease_until<current_timestamp) "
                    + "order by created_at limit 1 for update skip locked",(row,index)->new Item(row.getString(1),row.getString(2),row.getString(3),row.getInt(4),UUID.randomUUID().toString()));
            if(rows.isEmpty()) return null;
            var found=rows.get(0);
            jdbc.update("update payment_outbox set lease_token=?,lease_until=? where event_id=?",found.token(),Timestamp.from(Instant.now().plusSeconds(30)),found.id());
            return found;
        });
        if(item==null) return false;
        try {
            var event=json.readValue(item.raw(),PaymentNotificationRequest.class);
            kafka.send(item.topic(),event.orderReference(),event).get(8,TimeUnit.SECONDS);
            tx.executeWithoutResult(status -> jdbc.update("update payment_outbox set published_at=current_timestamp,lease_token=null,lease_until=null "
                    + "where event_id=? and lease_token=?",item.id(),item.token()));
        } catch(Exception exception) {
            if(exception instanceof InterruptedException) Thread.currentThread().interrupt();
            tx.executeWithoutResult(status -> jdbc.update("update payment_outbox set lease_token=null,lease_until=null,retry_count=retry_count+1,next_retry_at=? "
                    + "where event_id=? and lease_token=?",Timestamp.from(Instant.now().plusSeconds(Math.min(300,5L << Math.min(item.retries(),5)))),item.id(),item.token()));
        }
        return true;
    }
    private record Item(String id,String topic,String raw,int retries,String token) { }
}
