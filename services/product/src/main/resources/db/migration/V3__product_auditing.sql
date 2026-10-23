ALTER TABLE product ADD COLUMN created_by varchar(255);
ALTER TABLE product ADD COLUMN updated_by varchar(255);
ALTER TABLE product ADD COLUMN created_at timestamp;
ALTER TABLE product ADD COLUMN updated_at timestamp;
ALTER TABLE product ADD COLUMN deleted_at timestamp;
