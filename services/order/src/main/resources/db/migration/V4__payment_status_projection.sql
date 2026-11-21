alter table customer_order add column payment_status varchar(16) not null default 'UNKNOWN';
create table order_payment_event_inbox (
    event_id varchar(255) primary key,
    order_id integer not null,
    payment_id integer not null,
    status varchar(16) not null,
    received_at timestamp with time zone not null default current_timestamp
);
