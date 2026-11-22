package com.duong.ecommerce.payment.service;

import com.duong.ecommerce.payment.config.StripeSettings;
import com.duong.ecommerce.payment.entity.PaymentAttempt;
import com.duong.ecommerce.payment.integration.stripe.StripeCheckoutClient;
import com.duong.ecommerce.payment.payload.response.PaymentCheckoutResponse;
import com.duong.module.common.exception.ConflictException;
import com.duong.module.common.exception.NotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class PaymentAttemptService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final StripeSettings settings;
    private final StripeCheckoutClient stripe;
    private final PaymentSettlementService settlement;
    private final TransactionTemplate tx;
    public PaymentAttemptService(JdbcTemplate jdbc, ObjectMapper json, StripeSettings settings,
            StripeCheckoutClient stripe, PaymentSettlementService settlement, PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.json=json; this.settings=settings; this.stripe=stripe; this.settlement=settlement;
        tx=new TransactionTemplate(manager); tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Called in the transaction that creates/locks its Payment. */
    public String create(int paymentId, int orderId, long amount) {
        String id=UUID.randomUUID().toString();
        var parameters=new TreeMap<String,String>();
        parameters.put("mode", "payment"); parameters.put("payment_method_types[0]", "card");
        parameters.put("line_items[0][quantity]", "1");
        parameters.put("line_items[0][price_data][currency]", "vnd");
        parameters.put("line_items[0][price_data][unit_amount]", Long.toString(amount));
        parameters.put("line_items[0][price_data][product_data][name]", "Order #"+orderId);
        parameters.put("client_reference_id", Integer.toString(orderId));
        parameters.put("metadata[attempt_id]", id); parameters.put("metadata[payment_id]", Integer.toString(paymentId));
        parameters.put("metadata[order_id]", Integer.toString(orderId));
        parameters.put("success_url", settings.successUrl()+(settings.successUrl().contains("?")?"&":"?")+"orderId="+orderId);
        parameters.put("cancel_url", settings.cancelUrl()+(settings.cancelUrl().contains("?")?"&":"?")+"orderId="+orderId);
        parameters.put("expires_at", Long.toString(Instant.now().plus(Duration.ofHours(1)).getEpochSecond()));
        String state=amount==0?"SUCCEEDED":"CREATING";
        try {
            jdbc.update("insert into payment_attempt(id,payment_id,provider,amount,currency_code,status,provider_request_key,request_json) "
                    + "values (?,?,?,?,?,?,?,?)", id,paymentId,amount==0?"FREE":"STRIPE",amount,"VND",state,
                    "stripe:create:"+id,json.writeValueAsString(parameters));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw new IllegalStateException("Cannot persist attempt",exception); }
        return id;
    }

    /** Claim commits before any HTTP call; a lease recovers process death. */
    public boolean processNext() {
        if(!settings.enabled()) return false;
        PaymentAttempt attempt=tx.execute(status -> {
            var rows=jdbc.query("select * from payment_attempt where provider='STRIPE' "
                    + "and status in ('CREATING','UNKNOWN','REQUIRES_ACTION','PROCESSING') "
                    + "and payment_id in (select id from payment where status<>'SUCCEEDED') "
                    + "and next_retry_at<=current_timestamp and (lease_until is null or lease_until<current_timestamp) "
                    + "order by created_at limit 1 for update skip locked", (row,index) -> new PaymentAttempt(
                    row.getString("id"),row.getInt("payment_id"),row.getLong("amount"),row.getString("currency_code"),
                    row.getString("status"),row.getString("provider_request_key"),row.getString("request_json"),
                    row.getString("provider_session_id"),row.getTimestamp("created_at").toInstant(),null,row.getInt("retry_count")));
            if(rows.isEmpty()) return null;
            var found=rows.get(0); String token=UUID.randomUUID().toString();
            jdbc.update("update payment_attempt set lease_token=?,lease_until=? where id=?",token,
                    Timestamp.from(Instant.now().plusSeconds(30)),found.id());
            return new PaymentAttempt(found.id(),found.paymentId(),found.amount(),found.currencyCode(),found.status(),
                    found.providerRequestKey(),found.requestJson(),found.sessionId(),found.createdAt(),token,found.retries());
        });
        if(attempt==null) return false;
        try {
            if(attempt.sessionId()==null && attempt.createdAt().isBefore(Instant.now().minus(Duration.ofHours(23)))) {
                fail(attempt,"REVIEW_REQUIRED","IDEMPOTENCY_WINDOW_ELAPSED",false); return true;
            }
            JsonNode session=attempt.sessionId()==null
                    ?stripe.createSession(attempt.providerRequestKey(),json.readValue(attempt.requestJson(),new TypeReference<Map<String,String>>(){}))
                    :stripe.retrieveSession(attempt.sessionId());
            tx.executeWithoutResult(status -> {
                String owner=jdbc.queryForObject("select lease_token from payment_attempt where id=?",String.class,attempt.id());
                if(!attempt.leaseToken().equals(owner)) return;
                applySession(attempt.id(),session);
                jdbc.update("update payment_attempt set lease_token=null,lease_until=null,next_retry_at=?,updated_at=current_timestamp where id=?",
                        Timestamp.from(Instant.now().plusSeconds(60)),attempt.id());
            });
        } catch(StripeCheckoutClient.StripeCallException exception) {
            String state=exception.retryable()?"UNKNOWN":(attempt.sessionId()!=null || attempt.retries()>0?"REVIEW_REQUIRED":"FAILED");
            fail(attempt,state,exception.getMessage(),exception.retryable());
        } catch(InvalidSessionException exception) {
            fail(attempt,"REVIEW_REQUIRED","SESSION_MISMATCH",false);
        } catch(Exception exception) {
            fail(attempt,"UNKNOWN","RESPONSE_UNCERTAIN",true);
        }
        return true;
    }

    /** Caller holds a local transaction. Both webhook and reconciliation use this transition. */
    public void applySession(String attemptId, JsonNode session) {
        var rows=jdbc.query("select payment_id from payment_attempt where id=?",(row,index)->row.getInt(1),attemptId);
        if(rows.isEmpty()) throw new NotFoundException("Payment attempt not found");
        int paymentId=rows.get(0);
        var payment=jdbc.queryForMap("select * from payment where id=? for update",paymentId);
        var attempt=jdbc.queryForMap("select * from payment_attempt where id=? for update",attemptId);
        JsonNode metadata=session.path("metadata");
        String sessionId=session.path("id").asText();
        if(session.path("livemode").asBoolean(true) || !sessionId.matches("cs_test_[A-Za-z0-9_]+")
                || !"checkout.session".equals(session.path("object").asText())
                || !"payment".equals(session.path("mode").asText())
                || !"vnd".equals(session.path("currency").asText())
                || !session.path("amount_total").isIntegralNumber() || !session.path("amount_total").canConvertToLong() || session.path("amount_total").longValue()!=((Number)attempt.get("amount")).longValue()
                || !attemptId.equals(metadata.path("attempt_id").asText())
                || !Integer.toString(paymentId).equals(metadata.path("payment_id").asText())
                || !payment.get("order_id").toString().equals(metadata.path("order_id").asText())
                || (attempt.get("provider_session_id")!=null && !sessionId.equals(attempt.get("provider_session_id")))) {
            throw new InvalidSessionException();
        }
        if("SUCCEEDED".equals(attempt.get("status"))) return;
        String url=session.path("url").isTextual()?session.path("url").asText():null;
        if(url!=null) {
            var uri=java.net.URI.create(url);
            if(!"https".equals(uri.getScheme()) || !"checkout.stripe.com".equals(uri.getHost())) throw new InvalidSessionException();
        }
        String state;
        if("paid".equals(session.path("payment_status").asText())) state="SUCCEEDED";
        else if("expired".equals(session.path("status").asText())) state="EXPIRED";
        else if("complete".equals(session.path("status").asText())) state="PROCESSING";
        else if(java.util.Set.of("EXPIRED","FAILED").contains(attempt.get("status"))) return;
        else state="REQUIRES_ACTION";
        jdbc.update("update payment_attempt set provider_session_id=?,checkout_url=?,expires_at=?,status=?,failure_code=null,updated_at=current_timestamp where id=?",
                sessionId,url,session.path("expires_at").canConvertToLong()?Timestamp.from(Instant.ofEpochSecond(session.path("expires_at").longValue())):null,state,attemptId);
        if("SUCCEEDED".equals(state)) settlement.succeed(paymentId);
        else if(!"SUCCEEDED".equals(payment.get("status"))) {
            jdbc.update("update payment set status=? where id=? and ?=(select id from payment_attempt where payment_id=? order by created_at desc,id desc limit 1)",
                    "EXPIRED".equals(state)?"EXPIRED":"PENDING",paymentId,attemptId,paymentId);
        }
    }

    private void fail(PaymentAttempt attempt,String state,String code,boolean retry) {
        tx.executeWithoutResult(status -> {
            // Use the same payment-before-attempt lock order as webhook settlement.
            jdbc.queryForObject("select id from payment where id=? for update",Integer.class,attempt.paymentId());
            // Match lease AND mutable states: a webhook may have completed this attempt during HTTP.
            int changed=jdbc.update("update payment_attempt set status=?,failure_code=?,retry_count=retry_count+1,next_retry_at=?,"
                    + "lease_token=null,lease_until=null,updated_at=current_timestamp where id=? and lease_token=? "
                    + "and status not in ('SUCCEEDED','EXPIRED','FAILED')",state,code,
                    Timestamp.from(Instant.now().plusSeconds(Math.min(300,5L << Math.min(attempt.retries(),5)))),attempt.id(),attempt.leaseToken());
            if(changed>0 && !retry && "FAILED".equals(state)) jdbc.update("update payment set status='FAILED' where id=? and status<>'SUCCEEDED'",attempt.paymentId());
        });
    }

    public PaymentCheckoutResponse checkoutByOrder(int orderId) {
        var ids=jdbc.query("select id from payment where order_id=?",(row,index)->row.getInt(1),orderId);
        if(ids.isEmpty()) throw new NotFoundException("Payment not found for order");
        return checkout(ids.get(0));
    }
    public PaymentCheckoutResponse checkout(int paymentId) {
        var rows=jdbc.query("select p.*,a.id attempt_id,a.status attempt_status,a.checkout_url,a.expires_at,a.failure_code "
                + "from payment p left join payment_attempt a on a.id=(select id from payment_attempt where payment_id=p.id "
                + "order by created_at desc,id desc limit 1) where p.id=?",(row,index)->new PaymentCheckoutResponse(
                row.getInt("id"),row.getInt("order_id"),row.getBigDecimal("amount"),row.getString("currency_code"),row.getString("status"),
                row.getString("attempt_id"),row.getString("attempt_status"),
                "SUCCEEDED".equals(row.getString("status")) || (row.getTimestamp("expires_at")!=null && row.getTimestamp("expires_at").toInstant().isBefore(Instant.now()))
                        ?null:row.getString("checkout_url"),
                row.getTimestamp("expires_at")==null?null:row.getTimestamp("expires_at").toInstant(),row.getString("failure_code")),paymentId);
        if(rows.isEmpty()) throw new NotFoundException("Payment not found");
        return rows.get(0);
    }
    /** Called under the idempotency execution transaction; no new attempt while result is uncertain. */
    public String retry(int paymentId) {
        var rows=jdbc.queryForList("select * from payment where id=? for update",paymentId);
        if(rows.isEmpty()) throw new NotFoundException("Payment not found");
        var payment=rows.get(0);
        if("SUCCEEDED".equals(payment.get("status")) || "UNKNOWN".equals(payment.get("status")))
            throw new ConflictException("Payment cannot start another attempt");
        var current=checkout(paymentId);
        if(!java.util.Set.of("FAILED","EXPIRED").contains(current.attemptStatus()))
            throw new ConflictException("Existing payment attempt must be resolved before starting another");
        String id=create(paymentId,((Number)payment.get("order_id")).intValue(),((java.math.BigDecimal)payment.get("amount")).longValueExact());
        jdbc.update("update payment set status='PENDING' where id=?",paymentId);
        return id;
    }
    public static class InvalidSessionException extends RuntimeException { }
}
