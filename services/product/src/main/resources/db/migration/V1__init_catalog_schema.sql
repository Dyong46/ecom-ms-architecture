create table product (
    id varchar(36) primary key,
    name varchar(255) not null,
    slug varchar(255) not null unique,
    description text,
    created_at timestamp not null,
    updated_at timestamp not null
);
create table product_variant (
    id varchar(36) primary key,
    sku varchar(255) not null unique,
    price numeric(12, 2) not null,
    stock_quantity integer not null default 0,
    product_id varchar(36) not null references product(id) on delete cascade,
    created_at timestamp not null,
    updated_at timestamp not null
);
create index idx_product_variant_product_id on product_variant(product_id);
create table product_option (
    id varchar(36) primary key,
    name varchar(100) not null unique
);
create table option_value (
    id varchar(36) primary key,
    option_value varchar(100) not null,
    synonyms jsonb,
    option_id varchar(36) not null references product_option(id) on delete cascade,
    unique (option_id, option_value)
);
create table product_variant_option_value (
    product_variant_id varchar(36) not null references product_variant(id) on delete cascade,
    option_value_id varchar(36) not null references option_value(id) on delete cascade,
    primary key (product_variant_id, option_value_id)
);
create index idx_variant_option_value_id on product_variant_option_value(option_value_id);
