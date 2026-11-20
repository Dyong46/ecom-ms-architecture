create table idempotency_request
(
    request_key         varchar(128) primary key,
    request_fingerprint varchar(64) not null,
    operation_id        varchar(36) not null unique,
    result_json         text,
    created_at          timestamp   not null default current_timestamp,
    completed_at        timestamp
);
