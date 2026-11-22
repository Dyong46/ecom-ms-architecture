package com.duong.ecommerce.payment;

import com.duong.ecommerce.payment.payload.request.Customer;
import com.duong.ecommerce.payment.payload.request.PaymentRequest;
import com.duong.ecommerce.payment.entity.enumeration.PaymentMethodEnum;
import com.duong.ecommerce.payment.payload.event.PaymentNotificationRequest;
import com.duong.ecommerce.payment.service.*;
import com.duong.module.common.exception.ConflictException;
import com.duong.module.kafka.service.KafkaPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stripe.net.Webhook;
import com.sun.net.httpserver.HttpServer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"spring.cloud.config.enabled=false","eureka.client.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate","payment.workers.enabled=false","payment.stripe.enabled=true",
        "payment.stripe.secret-key=sk_test_fixture","payment.stripe.webhook-secret=whsec_fixture",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://127.0.0.1:1/issuer",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://127.0.0.1:1/jwks",
        "spring.kafka.admin.auto-create=false","spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "spring.kafka.producer.properties.spring.json.type.mapping=paymentConfirmation:com.duong.ecommerce.payment.payload.event.PaymentNotificationRequest",
        "spring.kafka.producer.properties.max.block.ms=5000","application.events.payment-topic=payment-stripe-fixture"})
@AutoConfigureMockMvc
@EmbeddedKafka(kraft=true,partitions=1,topics="payment-stripe-fixture",bootstrapServersProperty="spring.kafka.bootstrap-servers")
@Testcontainers
class StripeLifecyclePostgresTest {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    static HttpServer provider;
    static final ObjectMapper json=new ObjectMapper();
    static final Map<String,ObjectNode> sessions=new ConcurrentHashMap<>();
    static final Map<String,String> keys=new ConcurrentHashMap<>();
    static final Map<String,Map<String,String>> forms=new ConcurrentHashMap<>();
    static final AtomicInteger requests=new AtomicInteger();
    static final AtomicBoolean loseResponse=new AtomicBoolean();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) throws Exception {
        provider=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        provider.createContext("/v1/checkout/sessions",exchange -> {
            try {
                if("POST".equals(exchange.getRequestMethod())) {
                    requests.incrementAndGet();
                    String key=exchange.getRequestHeaders().getFirst("Idempotency-Key");
                    String raw=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                    var values=new TreeMap<String,String>();
                    for(String item:raw.split("&")) { var pair=item.split("=",2); values.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8)); }
                    forms.putIfAbsent(key,values);
                    if(!forms.get(key).equals(values)) throw new IllegalStateException("Provider parameters changed across retry");
                    String id=keys.computeIfAbsent(key,k->"cs_test_"+UUID.randomUUID().toString().replace("-",""));
                    sessions.computeIfAbsent(id,k->{
                        ObjectNode session=json.createObjectNode();
                        session.put("object","checkout.session").put("id",id).put("mode","payment").put("livemode",false)
                                .put("currency","vnd").put("amount_total",Long.parseLong(values.get("line_items[0][price_data][unit_amount]")))
                                .put("status","open").put("payment_status","unpaid").put("url","https://checkout.stripe.com/c/pay/"+id)
                                .put("expires_at",Long.parseLong(values.get("expires_at")));
                        session.putObject("metadata").put("attempt_id",values.get("metadata[attempt_id]"))
                                .put("payment_id",values.get("metadata[payment_id]")).put("order_id",values.get("metadata[order_id]"));
                        return session;
                    });
                    if(loseResponse.getAndSet(false)) {
                        byte[] error="{}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(500,error.length);exchange.getResponseBody().write(error);
                    } else {
                        byte[] body=json.writeValueAsBytes(sessions.get(id));exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);
                    }
                } else {
                    String id=exchange.getRequestURI().getPath().substring("/v1/checkout/sessions/".length());
                    byte[] body=json.writeValueAsBytes(sessions.get(id));exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);
                }
            } finally { exchange.close(); }
        });
        provider.start();
        r.add("payment.stripe.api-base-url",()->"http://127.0.0.1:"+provider.getAddress().getPort());
        r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);
        r.add("spring.datasource.password",postgres::getPassword);r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
    }
    @AfterAll static void stop() { provider.stop(0); }
    @Autowired PaymentCreationService creation;
    @Autowired PaymentAttemptService attempts;
    @Autowired StripeWebhookInbox inbox;
    @Autowired PaymentOutboxPublisher outbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired EmbeddedKafkaBroker broker;
    @SpyBean KafkaPublisher kafka;

    @BeforeEach void resetData() {
        for(String table:List.of("payment_outbox","stripe_webhook_inbox","payment_attempt","idempotency_request","payment")) jdbc.update("delete from "+table);
        sessions.clear();keys.clear();forms.clear();requests.set(0);loseResponse.set(false);reset(kafka);
    }
    PaymentRequest payment(String amount) {
        return new PaymentRequest(new BigDecimal(amount),PaymentMethodEnum.CREDIT_CARD,7,"ORDER-7",new Customer("c1","Ada","Lovelace","ada@example.test"));
    }
    int create(String amount) { return creation.create(UUID.randomUUID().toString(),payment(amount)); }
    ObjectNode currentSession() { return sessions.values().iterator().next().deepCopy(); }
    String event(ObjectNode session,String type,String id) throws Exception {
        var event=json.createObjectNode().put("id",id).put("object","event").put("type",type).put("livemode",false).put("created",Instant.now().getEpochSecond());
        event.putObject("data").set("object",session);return json.writeValueAsString(event);
    }
    void deliver(String raw) throws Exception {
        mvc.perform(post("/webhooks/stripe").contentType("application/json")
                .header("Stripe-Signature",Webhook.Signature.generateSignatureHeader(raw,"whsec_fixture"))
                .content(raw)).andExpect(status().isOk());
    }
    String eventId() { return "evt_"+UUID.randomUUID().toString().replace("-",""); }
    int count(String table) { return jdbc.queryForObject("select count(*) from "+table,Integer.class); }

    @Test void hostedCheckoutSettlesViaSignedInboxAndPublishesRealKafkaOutbox() throws Exception {
        int id=create("50000.00");
        assertThat(attempts.checkout(id).status()).isEqualTo("PENDING");assertThat(count("payment_outbox")).isZero();
        assertThat(attempts.processNext()).isTrue();
        var checkout=attempts.checkout(id);assertThat(checkout.attemptStatus()).isEqualTo("REQUIRES_ACTION");
        assertThat(checkout.checkoutUrl()).startsWith("https://checkout.stripe.com/");
        assertThat(forms.values().iterator().next()).containsEntry("line_items[0][price_data][unit_amount]","50000")
                .containsEntry("line_items[0][price_data][currency]","vnd");
        var session=currentSession().put("status","complete").put("payment_status","paid");
        String raw=event(session,"checkout.session.completed",eventId());deliver(raw);deliver(raw);
        assertThat(count("stripe_webhook_inbox")).isEqualTo(1);assertThat(count("payment_outbox")).isZero();
        assertThat(inbox.processNext()).isTrue();assertThat(attempts.checkout(id).status()).isEqualTo("SUCCEEDED");
        assertThat(count("payment_outbox")).isEqualTo(1);assertThat(outbox.processNext()).isTrue();
        assertThat(jdbc.queryForObject("select published_at is not null from payment_outbox",Boolean.class)).isTrue();
        var props=KafkaTestUtils.consumerProps("stripe-contract-"+UUID.randomUUID(),"false",broker);
        props.put("auto.offset.reset","earliest");
        try(var consumer=new KafkaConsumer<String,String>(props,new StringDeserializer(),new StringDeserializer())) {
            consumer.subscribe(List.of("payment-stripe-fixture"));boolean found=false;long until=System.nanoTime()+Duration.ofSeconds(10).toNanos();
            while(!found && System.nanoTime()<until) for(var record:consumer.poll(Duration.ofMillis(500))) {
                var payload=json.readTree(record.value());if(payload.path("paymentId").asInt()==id) {
                    assertThat(payload.path("currencyCode").asText()).isEqualTo("VND");
                    assertThat(payload.path("orderId").asInt()).isEqualTo(7);
                    assertThat(new String(record.headers().lastHeader("__TypeId__").value(),StandardCharsets.UTF_8)).isEqualTo("paymentConfirmation");found=true;
                }
            }
            assertThat(found).isTrue();
        }
        deliver(event(session.put("status","expired").put("payment_status","unpaid"),"checkout.session.expired",eventId()));
        inbox.processNext();assertThat(attempts.checkout(id).status()).isEqualTo("SUCCEEDED");assertThat(count("payment_outbox")).isEqualTo(1);
    }

    @Test void lostCreationResponseRetriesOneProviderSessionWithTheSameParameters() {
        loseResponse.set(true);int id=create("50000");attempts.processNext();
        assertThat(attempts.checkout(id).attemptStatus()).isEqualTo("UNKNOWN");
        assertThatThrownBy(()->creation.retry(UUID.randomUUID().toString(),id)).isInstanceOf(ConflictException.class);
        jdbc.update("update payment_attempt set next_retry_at=current_timestamp");attempts.processNext();
        assertThat(requests.get()).isEqualTo(2);assertThat(sessions).hasSize(1);assertThat(forms).hasSize(1);
        assertThat(attempts.checkout(id).attemptStatus()).isEqualTo("REQUIRES_ACTION");
    }

    @Test void webhookCanSettleBeforeTheLostSessionResponseIsRecorded() throws Exception {
        loseResponse.set(true);int id=create("50000");attempts.processNext();
        var paid=currentSession().put("status","complete").put("payment_status","paid");
        deliver(event(paid,"checkout.session.completed",eventId()));inbox.processNext();
        assertThat(attempts.checkout(id).status()).isEqualTo("SUCCEEDED");assertThat(count("payment_outbox")).isEqualTo(1);
    }

    @Test void invalidSignaturesAndMismatchedAmountsCannotMarkPaid() throws Exception {
        int id=create("50000");attempts.processNext();
        var paid=currentSession().put("status","complete").put("payment_status","paid").put("amount_total",50001);
        var raw=event(paid,"checkout.session.completed",eventId());
        mvc.perform(post("/webhooks/stripe").contentType("application/json").header("Stripe-Signature","t=1,v1=invalid").content(raw)).andExpect(status().isBadRequest());
        mvc.perform(post("/webhooks/stripe").contentType("application/json").header("Stripe-Signature",Webhook.Signature.generateSignatureHeader(raw,"whsec_fixture",Instant.now().minusSeconds(600).getEpochSecond())).content(raw)).andExpect(status().isBadRequest());
        assertThat(count("stripe_webhook_inbox")).isZero();deliver(raw);inbox.processNext();
        assertThat(jdbc.queryForObject("select status from stripe_webhook_inbox",String.class)).isEqualTo("REJECTED");
        assertThat(attempts.checkout(id).status()).isEqualTo("PENDING");assertThat(count("payment_outbox")).isZero();
    }

    @Test void zeroAmountBypassesStripeAndOutboxRetriesKeepTheEventId() {
        int id=create("0.00");assertThat(attempts.checkout(id).status()).isEqualTo("SUCCEEDED");
        assertThat(attempts.processNext()).isFalse();assertThat(requests.get()).isZero();
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable"))).doCallRealMethod()
                .when(kafka).send(anyString(),anyString(),any());
        outbox.processNext();assertThat(jdbc.queryForObject("select published_at is null from payment_outbox",Boolean.class)).isTrue();
        jdbc.update("update payment_outbox set next_retry_at=current_timestamp");outbox.processNext();
        var events=ArgumentCaptor.forClass(Object.class);verify(kafka,times(2)).send(anyString(),anyString(),events.capture());
        assertThat(((PaymentNotificationRequest)events.getAllValues().get(0)).eventId()).isEqualTo(((PaymentNotificationRequest)events.getAllValues().get(1)).eventId());
        assertThat(jdbc.queryForObject("select published_at is not null from payment_outbox",Boolean.class)).isTrue();
    }

    @Test void uncertainCreationStopsBeforeProviderIdempotencyRetentionCanExpire() {
        loseResponse.set(true);int id=create("50000");attempts.processNext();
        jdbc.update("update payment_attempt set created_at=current_timestamp-interval '24 hours',next_retry_at=current_timestamp");
        attempts.processNext();
        assertThat(attempts.checkout(id).attemptStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(requests.get()).isEqualTo(1);
        assertThatThrownBy(()->creation.retry(UUID.randomUUID().toString(),id)).isInstanceOf(ConflictException.class);
    }

    @Test void expiredAttemptCanStartOneExplicitIdempotentRetry() throws Exception {
        int id=create("50000");attempts.processNext();
        deliver(event(currentSession().put("status","expired"),"checkout.session.expired",eventId()));inbox.processNext();
        var oldSession=currentSession();
        var key=UUID.randomUUID().toString();String next=creation.retry(key,id);
        deliver(event(oldSession.put("status","expired"),"checkout.session.expired",eventId()));inbox.processNext();
        assertThat(attempts.checkout(id).status()).isEqualTo("PENDING");
        assertThat(creation.retry(key,id)).isEqualTo(next);assertThat(count("payment_attempt")).isEqualTo(2);assertThat(count("payment")).isEqualTo(1);
        assertThatThrownBy(()->creation.retry(UUID.randomUUID().toString(),id)).isInstanceOf(ConflictException.class);
    }
}
