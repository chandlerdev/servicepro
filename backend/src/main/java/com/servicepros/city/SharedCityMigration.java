package com.servicepros.city;

import javax.sql.DataSource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time upgrade: cities used to be per admin (an {@code owner_id} column, unique per admin + name). They are
 * now one shared list managed by the Super Admin. On a database that still has {@code owner_id}, this keeps one
 * row per name (the oldest), then drops the column. Customers and routes refer to cities by name, so nothing
 * else changes. Does nothing on an up-to-date database.
 */
@Slf4j
@Component
@Order(-10)
public class SharedCityMigration implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public SharedCityMigration(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer hasOwner;
        try {
            hasOwner = jdbc.queryForObject("select count(*) from information_schema.columns "
                    + "where lower(table_name) = 'cities' and lower(column_name) = 'owner_id' "
                    + "and table_schema = database()", Integer.class);
        } catch (Exception e) {
            log.debug("City migration check skipped: {}", e.getMessage());
            return;
        }
        if (hasOwner == null || hasOwner == 0) return;

        log.info("Upgrading cities to one shared list (removing per-admin owner_id)…");
        int removed = jdbc.update("delete c from cities c join cities k "
                + "on lower(k.name) = lower(c.name) and k.id < c.id");
        quietly("alter table cities drop index uk_city_owner_name");
        jdbc.execute("alter table cities drop column owner_id");
        quietly("create unique index uk_city_name on cities (name)");
        log.info("Cities upgraded: {} duplicate name(s) merged", removed);
    }

    private void quietly(String sql) {
        try {
            jdbc.execute(sql);
        } catch (Exception e) {
            log.debug("Skipped '{}': {}", sql, e.getMessage());
        }
    }
}
