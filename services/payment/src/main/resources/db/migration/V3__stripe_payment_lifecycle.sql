-- Preserve historical records without asserting that money was actually collected.
alter table payment add column currency_code varchar(3);
alter table payment add column status varchar(16) not null default 'UNKNOWN';
alter table payment add column order_reference varchar(255);
alter table payment add column customer_firstname varchar(255);
alter table payment add column customer_lastname varchar(255);
alter table payment add column customer_email varchar(255);
alter table payment add column paid_at timestamp with time zone;
create table payment_attempt (
    id varchar(36) primary key,
    payment_id integer not null references payment(id),
    provider varchar(16) not null,
    amount bigint not null,
    currency_code varchar(3) not null,
    status varchar(32) not null,
    provider_request_key varchar(128) not null unique,
    request_json text not null,
    provider_session_id varchar(255) unique,
    checkout_url text,
    expires_at timestamp with time zone,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    next_retry_at timestamp with time zone not null default current_timestamp,
    lease_token varchar(36),
    lease_until timestamp with time zone,
    retry_count integer not null default 0,
    failure_code varchar(64)
);
create index idx_attempt_payment on payment_attempt(payment_id, created_at);
create index idx_attempt_work on payment_attempt(status, next_retry_at);
create table stripe_webhook_inbox (
    event_id varchar(255) primary key,
    event_type varchar(100) not null,
    payload_json text not null,
    status varchar(16) not null,
    received_at timestamp with time zone not null default current_timestamp,
    processed_at timestamp with time zone,
    next_retry_at timestamp with time zone not null default current_timestamp,
    retry_count integer not null default 0,
    failure_code varchar(64)
);
create index idx_webhook_work on stripe_webhook_inbox(status, next_retry_at);
create table payment_outbox (
    event_id varchar(36) primary key,
    payment_id integer not null references payment(id),
    event_type varchar(64) not null,
    topic varchar(249) not null,
    payload_json text not null,
    created_at timestamp with time zone not null default current_timestamp,
    published_at timestamp with time zone,
    next_retry_at timestamp with time zone not null default current_timestamp,
    lease_token varchar(36),
    lease_until timestamp with time zone,
    retry_count integer not null default 0,
    unique(payment_id, event_type)
);
create index idx_outbox_work on payment_outbox(published_at, next_retry_at);
