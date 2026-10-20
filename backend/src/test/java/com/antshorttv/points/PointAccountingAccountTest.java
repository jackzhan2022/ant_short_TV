package com.antshorttv.points;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class PointAccountingAccountTest {
    private JdbcTemplate jdbc;
    private PointAccountingService accounting;
    private TransactionTemplate transactions;
    private final AtomicInteger initializationWrites = new AtomicInteger();

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource(
            "jdbc:h2:mem:accounts_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source) {
            @Override
            public int update(String sql, Object... args) {
                if (sql.stripLeading().toLowerCase(java.util.Locale.ROOT).startsWith("insert into team_point_account")) {
                    initializationWrites.incrementAndGet();
                }
                return super.update(sql, args);
            }
        };
        jdbc.execute("""
            create table team_point_account (
                id bigint auto_increment primary key, tenant_id bigint not null unique,
                balance decimal(24,8) not null, total_granted decimal(24,8) not null,
                total_consumed decimal(24,8) not null, reserved_balance decimal(24,8) not null default 0,
                total_reserved decimal(24,8) not null default 0, total_released decimal(24,8) not null default 0,
                total_refunded decimal(24,8) not null default 0, version int not null default 0,
                trial_balance decimal(24,8) not null default 0, created_at timestamp not null,
                updated_at timestamp not null)
            """);
        accounting = new PointAccountingService(jdbc, null);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    @Test
    void existingAccountLocksWithoutInitializationWritesOrBalanceChanges() {
        transactions.executeWithoutResult(status -> accounting.ensureAccount(37L));
        jdbc.update("update team_point_account set balance=123, trial_balance=23, version=7 where tenant_id=37");
        var before = jdbc.queryForMap("select * from team_point_account where tenant_id=37");
        initializationWrites.set(0);

        transactions.executeWithoutResult(status -> {
            accounting.ensureAccount(37L);
            accounting.lockAccount(37L);
        });

        assertThat(initializationWrites.get()).isZero();
        assertThat(jdbc.queryForMap("select * from team_point_account where tenant_id=37")).isEqualTo(before);
    }

    @Test
    void firstUseCreatesOneZeroBalanceAccount() {
        transactions.executeWithoutResult(status -> accounting.lockAccount(37L));
        var account = jdbc.queryForMap("select * from team_point_account where tenant_id=37");
        for (String column : java.util.List.of("BALANCE", "TOTAL_GRANTED", "TOTAL_CONSUMED", "RESERVED_BALANCE",
            "TOTAL_RESERVED", "TOTAL_RELEASED", "TOTAL_REFUNDED", "TRIAL_BALANCE")) {
            assertThat((java.math.BigDecimal) account.get(column)).isZero();
        }
        assertThat(jdbc.queryForObject("select count(*) from team_point_account", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentFirstUseCreatesOneAccountAndPreservesAllUpdates() throws Exception {
        var workers = Executors.newFixedThreadPool(8);
        var start = new CountDownLatch(1);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 8; i++) {
                futures.add(workers.submit(() -> {
                    try {
                        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                    transactions.executeWithoutResult(status -> {
                        accounting.lockAccount(37L);
                        jdbc.update("update team_point_account set balance=balance+1 where tenant_id=37");
                    });
                }));
            }
            start.countDown();
            for (var future : futures) future.get(20, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from team_point_account", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select balance from team_point_account where tenant_id=37", java.math.BigDecimal.class))
            .isEqualByComparingTo("8");
    }
}
