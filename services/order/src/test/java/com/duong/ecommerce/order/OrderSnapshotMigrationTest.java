package com.duong.ecommerce.order;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderSnapshotMigrationTest {
    @Test
    void upgradesExistingOrderWithoutInventingHistoricalSnapshots() throws Exception {
        String url = "jdbc:h2:mem:migration_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("1").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into customer_order(reference, total_amount, payment_method, customer_id, create_at)
                    values ('HISTORICAL', 25.00, 'PAYPAL', 'old-customer', current_timestamp)
                    """);
        }
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var row = statement.executeQuery("select * from customer_order where reference = 'HISTORICAL'")) {
            assertThat(row.next()).isTrue();
            assertThat(row.getBigDecimal("total_amount")).isEqualByComparingTo("25.00");
            for (String field : new String[]{"customer_full_name", "customer_email", "recipient_full_name",
                    "recipient_phone", "shipping_address_line1", "shipping_fee", "items_subtotal", "currency_code"}) {
                assertThat(row.getObject(field)).as(field).isNull();
            }
        }
    }
}
