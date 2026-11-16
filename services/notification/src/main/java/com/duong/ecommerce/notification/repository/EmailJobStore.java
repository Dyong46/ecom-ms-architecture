package com.duong.ecommerce.notification.repository;

import com.duong.ecommerce.notification.entity.enumeration.DeliveryStatusEnum;
import com.duong.ecommerce.notification.entity.Notification;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class EmailJobStore {
    public static final int MAX_ATTEMPTS = 5;
    private final MongoTemplate mongo;
    private volatile boolean indexesReady;

    public EmailJobStore(MongoTemplate mongo) { this.mongo = mongo; }

    private synchronized void ensureIndexes() {
        if (!indexesReady) {
            mongo.indexOps(Notification.class).ensureIndex(new Index().on("status", Sort.Direction.ASC).on("nextRetryAt", Sort.Direction.ASC));
            mongo.indexOps(Notification.class).ensureIndex(new Index().on("status", Sort.Direction.ASC).on("leaseUntil", Sort.Direction.ASC));
            indexesReady = true;
        }
    }

    public void enqueue(Notification job) {
        if (job.getEventId() == null || !job.getEventId().equals(job.getId())) {
            throw new IllegalArgumentException("Job _id must equal eventId");
        }
        ensureIndexes();
        try {
            mongo.insert(job);
        } catch (DuplicateKeyException exception) {
            // Majority read confirms the previously inserted job before listener acknowledgement.
            if (mongo.findById(job.getId(), Notification.class) == null) throw exception;
        }
    }

    public Notification claim(Instant now, Duration lease) {
        ensureIndexes();
        // A crash on the last attempt must not leave a job PROCESSING forever.
        var exhausted = Query.query(Criteria.where("status").is(DeliveryStatusEnum.PROCESSING)
                .and("leaseUntil").lte(now).and("attemptCount").gte(MAX_ATTEMPTS));
        mongo.updateMulti(exhausted, clearLease(new Update().set("status", DeliveryStatusEnum.FAILED)
                .set("lastError", "LEASE_EXPIRED_AT_ATTEMPT_LIMIT").unset("nextRetryAt")), Notification.class);
        var eligible = new Criteria().orOperator(
                Criteria.where("status").in(DeliveryStatusEnum.PENDING, DeliveryStatusEnum.RETRY).and("nextRetryAt").lte(now),
                Criteria.where("status").is(DeliveryStatusEnum.PROCESSING).and("leaseUntil").lte(now));
        var query = Query.query(new Criteria().andOperator(eligible, Criteria.where("attemptCount").lt(MAX_ATTEMPTS)))
                .with(Sort.by("nextRetryAt", "_id"));
        var update = new Update().set("status", DeliveryStatusEnum.PROCESSING).inc("attemptCount", 1)
                .set("lastAttemptAt", now).set("leaseUntil", now.plus(lease)).set("leaseToken", UUID.randomUUID().toString());
        return mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Notification.class);
    }

    public boolean markSent(Notification job, Instant now) {
        return finish(job, new Update().set("status", DeliveryStatusEnum.SENT).set("submittedAt", now)
                .unset("nextRetryAt").unset("lastError"));
    }

    public boolean markFailure(Notification job, String error, Instant retryAt) {
        var update = new Update().set("lastError", error).set("status", retryAt == null ? DeliveryStatusEnum.FAILED : DeliveryStatusEnum.RETRY);
        if (retryAt == null) update.unset("nextRetryAt"); else update.set("nextRetryAt", retryAt);
        return finish(job, update);
    }

    private boolean finish(Notification job, Update update) {
        var query = Query.query(Criteria.where("_id").is(job.getId()).and("status").is(DeliveryStatusEnum.PROCESSING)
                .and("leaseToken").is(job.getLeaseToken()));
        return mongo.updateFirst(query, clearLease(update), Notification.class).getModifiedCount() == 1;
    }

    private Update clearLease(Update update) { return update.unset("leaseUntil").unset("leaseToken"); }
}
