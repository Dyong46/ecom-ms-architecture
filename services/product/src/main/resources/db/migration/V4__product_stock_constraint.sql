ALTER TABLE product
    ADD CONSTRAINT chk_product_available_quantity_non_negative
    CHECK (available_quantity >= 0);
