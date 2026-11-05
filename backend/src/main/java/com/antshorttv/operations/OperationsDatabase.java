package com.antshorttv.operations;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Owns its pool without registering DataSource/JdbcTemplate beans (Boot must keep the business pool). */
@Component
public class OperationsDatabase {
    private final HikariDataSource pool;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    @Autowired public OperationsDatabase(DataSourceProperties properties) {
        var config = new HikariConfig();
        config.setJdbcUrl(properties.determineUrl());
        config.setUsername(properties.determineUsername());
        config.setPassword(properties.determinePassword());
        config.setDriverClassName(properties.determineDriverClassName());
        config.setPoolName("operations-monitoring");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(1000);
        config.setValidationTimeout(1000);
        config.setInitializationFailTimeout(-1);
        // A short independent socket budget prevents dead servers pinning the observer indefinitely.
        if (properties.determineUrl().startsWith("jdbc:mysql:")) {
            config.addDataSourceProperty("connectTimeout", "1000");
            config.addDataSourceProperty("socketTimeout", "1000");
            config.addDataSourceProperty("connectionTimeZone", "UTC");
            config.addDataSourceProperty("forceConnectionTimeZoneToSession", "true");
        }
        pool = new HikariDataSource(config);
        jdbc = new JdbcTemplate(pool);
        jdbc.setQueryTimeout(1);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(pool));
        transactions.setTimeout(1);
    }
    public OperationsDatabase(JdbcTemplate jdbc) {
        this.pool=null;
        this.jdbc=jdbc;
        this.transactions=new TransactionTemplate(new DataSourceTransactionManager(java.util.Objects.requireNonNull(jdbc.getDataSource())));
    }
    public JdbcTemplate jdbc() { return jdbc; }
    public TransactionTemplate transactions() { return transactions; }
    @PreDestroy public void close() { if(pool!=null) pool.close(); }
}
