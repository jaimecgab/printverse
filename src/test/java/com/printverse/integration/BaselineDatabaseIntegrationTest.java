package com.printverse.integration;

import com.printverse.dto.QuoteDtos;
import com.printverse.service.QuoteService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.seed.enabled=false",
        "spring.flyway.baseline-on-migrate=true",
        "spring.flyway.baseline-version=1"
})
@Testcontainers(disabledWithoutDocker = true)
class BaselineDatabaseIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static boolean prepared;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        prepareLegacyDatabase();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.jwt.secret",
                () -> "baseline-database-test-jwt-signing-key-at-least-32-characters");
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private QuoteService quoteService;

    @Test
    void baselinesV1AndBackfillsStableHistoricalSnapshots() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where type = 'BASELINE' and version = '1'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select updated_at = created_at from quotes where id = 1", Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForMap(
                "select customer_name_snapshot, customer_phone_snapshot from quotes where id = 1"))
                .containsEntry("customer_name_snapshot", "Legacy customer")
                .containsEntry("customer_phone_snapshot", "555-0100");
        assertThat(jdbcTemplate.queryForMap(
                "select customer_name_snapshot, customer_phone_snapshot from quotes where id = 2"))
                .containsEntry("customer_name_snapshot", null)
                .containsEntry("customer_phone_snapshot", null);
        assertThat(jdbcTemplate.queryForMap(
                "select material_name_snapshot, printer_name_snapshot, printer_model_snapshot "
                        + "from quote_items where id = 1"))
                .containsEntry("material_name_snapshot", "Legacy PLA")
                .containsEntry("printer_name_snapshot", "Legacy printer")
                .containsEntry("printer_model_snapshot", "Legacy model");

        jdbcTemplate.update("update customers set name = 'Renamed customer', phone = '555-9999' where id = 1");
        jdbcTemplate.update("update materials set name = 'Renamed material' where id = 1");
        jdbcTemplate.update("update printers set name = 'Renamed printer', model = 'New model' where id = 1");

        QuoteDtos.Response response = quoteService.get(1L);
        assertThat(response.customer().name()).isEqualTo("Legacy customer");
        assertThat(response.customer().phone()).isEqualTo("555-0100");
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.material().name()).isEqualTo("Legacy PLA");
            assertThat(item.printer().name()).isEqualTo("Legacy printer");
            assertThat(item.printer().model()).isEqualTo("Legacy model");
        });
    }

    private static synchronized void prepareLegacyDatabase() {
        if (prepared) {
            return;
        }
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__initial_schema.sql"))
                .execute(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("insert into customers (name, phone, email, created_at) values (?, ?, ?, now())",
                "Legacy customer", "555-0100", "legacy@example.com");
        jdbc.update("insert into materials (name, price_per_kg, active, created_at) values (?, ?, true, now())",
                "Legacy PLA", 350);
        jdbc.update("insert into printers (name, model, cost_per_hour, active, created_at) "
                        + "values (?, ?, ?, true, now())",
                "Legacy printer", "Legacy model", 10);
        jdbc.update("insert into quotes (quote_number, customer_id, status, created_at, valid_until, "
                        + "markup_percentage, discount_percentage, tax_enabled, tax_percentage, internal_cost, "
                        + "suggested_subtotal, final_subtotal, discount_amount, tax_amount, total, "
                        + "estimated_profit, real_margin_percentage) "
                        + "values ('PV-LEGACY', 1, 'ACCEPTED', now(), current_date + 30, "
                        + "40, 0, true, 16, 10, 14, 14, 0, 2.24, 16.24, 4, 28.5714)");
        jdbc.update("insert into quotes (quote_number, customer_id, status, created_at, valid_until, "
                        + "markup_percentage, discount_percentage, tax_enabled, tax_percentage, internal_cost, "
                        + "suggested_subtotal, final_subtotal, discount_amount, tax_amount, total, "
                        + "estimated_profit, real_margin_percentage) "
                        + "values ('PV-DRAFT', 1, 'DRAFT', now(), current_date + 30, "
                        + "40, 0, true, 16, 0, 0, 0, 0, 0, 0, 0, 0)");
        jdbc.update("insert into quote_items (quote_id, name, quantity, material_id, printer_id, weight_grams, "
                        + "print_time_minutes, failure_risk_percentage, material_price_per_kg_snapshot, "
                        + "printer_cost_per_hour_snapshot, material_cost_unit, machine_cost_unit, "
                        + "failure_risk_cost_unit, additional_charges_unit, internal_cost_unit, suggested_price_unit) "
                        + "values (1, 'Legacy part', 1, 1, 1, 10, 30, 0, 350, 10, 3.50, 5, 0, 0, 8.50, 11.90)");
        prepared = true;
    }
}
