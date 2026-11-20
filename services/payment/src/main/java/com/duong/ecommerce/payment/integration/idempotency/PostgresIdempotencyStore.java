package com.duong.ecommerce.payment.integration.idempotency;

import com.duong.module.common.exception.BadRequestException;
import com.duong.module.common.exception.ConflictException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;

/**
 * Service-local ledger: durable key binding, with business result committed atomically.
 */
@Component
public class PostgresIdempotencyStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;

    public PostgresIdempotencyStore(JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.json = json;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T execute(String key, Object input, TypeReference<T> responseType, Function<String, T> work) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new BadRequestException("Idempotency-Key must contain 1 to 128 letters, digits, '.', '_', ':' or '-'");
        }
        String fingerprint = fingerprint(input);
        // Keep the binding across business rollback/crash, without persisting partial business results.
        transactions.executeWithoutResult(status -> {
            jdbc.update("insert into idempotency_request(request_key, request_fingerprint, operation_id) "
                    + "values (?, ?, ?) on conflict do nothing", key, fingerprint, UUID.randomUUID().toString());
            String existing = jdbc.queryForObject("select request_fingerprint from idempotency_request where request_key = ?",
                    String.class, key);
            if (!fingerprint.equals(existing))
                throw new ConflictException("Idempotency-Key was already used with different input");
        });
        try {
            return transactions.execute(status -> {
                var entry = jdbc.queryForObject("select request_fingerprint, operation_id, result_json "
                                + "from idempotency_request where request_key = ? for update nowait",
                        (row, index) -> new Entry(row.getString(1), row.getString(2), row.getString(3)), key);
                if (!fingerprint.equals(entry.fingerprint()))
                    throw new ConflictException("Idempotency-Key was already used with different input");
                if (entry.result() != null) return read(entry.result(), responseType);
                T result = Objects.requireNonNull(work.apply(entry.operationId()), "Idempotency result is required");
                jdbc.update("update idempotency_request set result_json = ?, completed_at = current_timestamp where request_key = ?",
                        write(result), key);
                return result;
            });
        } catch (DataAccessException exception) {
            // PostgreSQL lock_not_available (NOWAIT) is not consistently translated by JdbcTemplate.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof java.sql.SQLException sql && "55P03".equals(sql.getSQLState())) {
                    throw new ConflictException("Request with this Idempotency-Key is still processing; retry with the same key");
                }
            }
            throw exception;
        }
    }

    private String fingerprint(Object input) {
        try {
            byte[] canonical = json.writeValueAsBytes(normalize(json.valueToTree(input)));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot fingerprint request", exception);
        }
    }

    private JsonNode normalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode normalized = json.createObjectNode();
            var keys = new TreeSet<String>();
            node.fieldNames().forEachRemaining(keys::add);
            keys.forEach(key -> normalized.set(key, normalize(node.get(key))));
            return normalized;
        }
        if (node.isArray()) {
            var normalized = json.createArrayNode();
            node.forEach(value -> normalized.add(normalize(value)));
            return normalized;
        }
        if (node.isNumber()) return DecimalNode.valueOf(node.decimalValue().stripTrailingZeros());
        return node;
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot store idempotency result", exception);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return json.readValue(value, type);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read idempotency result", exception);
        }
    }

    private record Entry(String fingerprint, String operationId, String result) {
    }
}
