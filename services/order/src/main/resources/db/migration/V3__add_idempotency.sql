create table idempotency_request
(
    request_key         varchar(128) primary key,
    request_fingerprint varchar(64) not null,
    operation_id        varchar(36) not null unique,
    result_json         text,
    created_at          timestamp   not null default current_timestamp,
    completed_at        timestamp
);

alter table idempotency_request
    add column business_reference varchar(255) not null unique;
create table order_checkout_draft
(
    operation_id varchar(36) primary key,
    order_id     integer not null unique references customer_order (id),
    context_json text    not null
);
alter table customer_order
    add column creation_status varchar(16) not null default 'COMPLETED';
