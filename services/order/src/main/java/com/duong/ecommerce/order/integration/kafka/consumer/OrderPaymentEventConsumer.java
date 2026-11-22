package com.duong.ecommerce.order.integration.kafka.consumer;

import com.duong.ecommerce.order.payload.event.PaymentSucceeded;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
@ConditionalOnProperty(name="order.payment-events.enabled",havingValue="true")
public class OrderPaymentEventConsumer {
    private final JdbcTemplate jdbc;
    @Transactional
    @KafkaListener(topics="${application.events.payment-topic:payment-topic}",groupId="${order.payment-events.group:order-payment-status}",
            autoStartup="${order.payment-events.listener-enabled:true}")
    public void receive(PaymentSucceeded event) {
        // Historical notifications without business IDs do not prove settlement of an order.
        if(event.eventId()==null || event.eventId().isBlank() || event.paymentId()==null || event.orderId()==null
                || event.amount()==null || event.currencyCode()==null) return;
        int inserted=jdbc.update("insert into order_payment_event_inbox(event_id,order_id,payment_id,status) values (?,?,?,'RECEIVED') on conflict do nothing",
                event.eventId(),event.orderId(),event.paymentId());
        if(inserted==0) return;
        var rows=jdbc.queryForList("select * from customer_order where id=? for update",event.orderId());
        if(rows.isEmpty() || event.amount().compareTo((java.math.BigDecimal)rows.get(0).get("total_amount"))!=0
                || !event.currencyCode().equals(rows.get(0).get("currency_code"))
                || !java.util.Objects.equals(event.orderReference(),rows.get(0).get("reference"))) {
            jdbc.update("update order_payment_event_inbox set status='REJECTED' where event_id=?",event.eventId());return;
        }
        jdbc.update("update customer_order set payment_status='SUCCEEDED' where id=?",event.orderId());
        jdbc.update("update order_payment_event_inbox set status='PROCESSED' where event_id=?",event.eventId());
    }
}
