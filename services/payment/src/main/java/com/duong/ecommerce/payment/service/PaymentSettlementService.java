package com.duong.ecommerce.payment.service;

import com.duong.ecommerce.payment.entity.enumeration.PaymentMethodEnum;
import com.duong.ecommerce.payment.payload.event.PaymentNotificationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class PaymentSettlementService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final String topic;
    public PaymentSettlementService(JdbcTemplate jdbc, ObjectMapper json,
            @Value("${application.events.payment-topic:payment-topic}") String topic) {
        this.jdbc=jdbc; this.json=json; this.topic=topic;
    }
    @Transactional
    public void succeed(int paymentId) {
        var event = jdbc.queryForObject("select * from payment where id=? for update", (row, index) -> {
            if ("SUCCEEDED".equals(row.getString("status"))) return null;
            return new PaymentNotificationRequest(row.getString("order_reference"), row.getBigDecimal("amount"),
                    PaymentMethodEnum.valueOf(row.getString("payment_method")), row.getString("customer_firstname"),
                    row.getString("customer_lastname"), row.getString("customer_email"), UUID.randomUUID().toString(),
                    paymentId, row.getInt("order_id"), row.getString("currency_code"));
        }, paymentId);
        if (event == null) return;
        jdbc.update("update payment set status='SUCCEEDED', paid_at=current_timestamp where id=?", paymentId);
        try {
            jdbc.update("insert into payment_outbox(event_id,payment_id,event_type,topic,payload_json) values (?,?,?,?,?) "
                    + "on conflict do nothing", event.eventId(), paymentId, "PAYMENT_SUCCEEDED", topic, json.writeValueAsString(event));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Cannot encode payment event", exception);
        }
    }
}
