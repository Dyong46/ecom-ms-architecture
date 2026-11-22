-- Historical snapshot values cannot be reconstructed from today's Customer/Product.
-- Keep new fields nullable for existing orders; all new orders populate them.
alter table customer_order
    add column customer_full_name varchar(511);
alter table customer_order
    add column customer_email varchar(255);
alter table customer_order
    add column recipient_full_name varchar(255);
alter table customer_order
    add column recipient_phone varchar(32);
alter table customer_order
    add column shipping_address_line1 varchar(255);
alter table customer_order
    add column shipping_address_line2 varchar(255);
alter table customer_order
    add column shipping_ward varchar(100);
alter table customer_order
    add column shipping_city varchar(100);
alter table customer_order
    add column shipping_postal_code varchar(32);
alter table customer_order
    add column shipping_country_code varchar(2);
alter table customer_order
    add column delivery_note varchar(1000);
alter table customer_order
    add column items_subtotal numeric(38, 2);
alter table customer_order
    add column shipping_fee numeric(38, 2);
alter table customer_order
    add column currency_code varchar(3);
alter table order_line
    add column variant_name text;
