package com.printverse.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.seed.enabled=false",
        "spring.flyway.baseline-on-migrate=false"
})
@Testcontainers(disabledWithoutDocker = true)
class FreshDatabaseIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.jwt.secret",
                () -> "fresh-database-test-jwt-signing-key-at-least-32-characters");
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @Test
    void appliesAllMigrationsAndValidatesTheJpaSchema() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");
        assertThat(environment.getActiveProfiles()).doesNotContain("dev");
        assertThat(columnNullability("quotes", "updated_at")).isEqualTo("NO");
        assertThat(columnNullability("quote_items", "material_name_snapshot")).isEqualTo("NO");
        assertThat(columnNullability("quote_items", "printer_name_snapshot")).isEqualTo("NO");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name = 'app_users'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where schemaname = 'public' "
                        + "and indexname = 'uq_production_item_active_printer'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from pg_constraint where conname = 'chk_production_item_in_progress_printer'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void repairsCancelledActiveItemsAndReleasesTheirBusyPrinterWhenAdoptingVersionSix() {
        String schema = "cancelled_order_adoption";
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion("5"))
                .load()
                .migrate();
        JdbcTemplate adoption = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        adoption.update("insert into " + schema + ".customers "
                + "(id, name, phone, created_at) values (1, 'Customer', '555-0100', now())");
        adoption.update("insert into " + schema + ".materials "
                + "(id, name, price_per_kg, active, created_at) values (1, 'PLA', 100, true, now())");
        adoption.update("insert into " + schema + ".printers "
                + "(id, name, cost_per_hour, active, created_at, operational_status) "
                + "values (1, 'Busy printer', 10, true, now(), 'BUSY')");
        adoption.update("insert into " + schema + ".quotes "
                + "(id, quote_number, customer_id, status, created_at, valid_until, markup_percentage, "
                + "discount_percentage, tax_enabled, tax_percentage, internal_cost, suggested_subtotal, "
                + "final_subtotal, discount_amount, tax_amount, total, estimated_profit, "
                + "real_margin_percentage, currency_code, updated_at) values "
                + "(1, 'PV-ADOPTION', 1, 'DRAFT', now(), current_date, 0, 0, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 'MXN', now())");
        adoption.update("insert into " + schema + ".quote_items "
                + "(id, quote_id, name, quantity, material_id, printer_id, weight_grams, "
                + "print_time_minutes, failure_risk_percentage, material_price_per_kg_snapshot, "
                + "printer_cost_per_hour_snapshot, material_cost_unit, machine_cost_unit, "
                + "failure_risk_cost_unit, additional_charges_unit, internal_cost_unit, "
                + "suggested_price_unit, material_name_snapshot, printer_name_snapshot) values "
                + "(1, 1, 'Part', 1, 1, 1, 10, 10, 0, 100, 10, 0, 0, 0, 0, 0, 0, 'PLA', 'Busy printer')");
        adoption.update("insert into " + schema + ".production_orders "
                + "(id, quote_id, order_number, status, priority, created_at, updated_at, cancelled_at) "
                + "values (1, 1, 'OP-ADOPTION', 'CANCELLED', 'NORMAL', now(), now(), now())");
        adoption.update("insert into " + schema + ".production_order_items "
                + "(id, production_order_id, quote_item_id, name, quantity, material_name, printer_name, "
                + "weight_grams, print_time_minutes, assigned_printer_id, completed_quantity, status) "
                + "values (1, 1, 1, 'Part', 1, 'PLA', 'Busy printer', 10, 10, 1, 0, 'IN_PROGRESS')");

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .load()
                .migrate();

        assertThat(adoption.queryForObject(
                "select status from " + schema + ".production_order_items where id = 1", String.class))
                .isEqualTo("BLOCKED");
        assertThat(adoption.queryForObject(
                "select operational_status from " + schema + ".printers where id = 1", String.class))
                .isEqualTo("AVAILABLE");
    }

    private String columnNullability(String table, String column) {
        return jdbcTemplate.queryForObject(
                "select is_nullable from information_schema.columns where table_schema = 'public' "
                        + "and table_name = ? and column_name = ?",
                String.class, table, column);
    }
}
