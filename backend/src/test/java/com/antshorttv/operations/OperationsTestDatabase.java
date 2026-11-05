package com.antshorttv.operations;

import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

public final class OperationsTestDatabase {
    public static JdbcTemplate create() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:ops_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("create table platform_permission(id bigint auto_increment primary key,code varchar(100),name varchar(100),resource varchar(100),action varchar(100),created_at datetime,updated_at datetime)");
        jdbc.execute("create table platform_role(id bigint primary key,code varchar(100))");
        jdbc.execute("create table platform_role_permission(role_id bigint,permission_id bigint,created_at datetime)");
        jdbc.update("insert into platform_role values(1,'PLATFORM_ADMIN'),(2,'ADMIN')");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V174__platform_operations_monitoring.sql")).execute(source);
        return jdbc;
    }
}
