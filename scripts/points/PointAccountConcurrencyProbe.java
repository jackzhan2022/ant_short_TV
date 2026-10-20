import com.antshorttv.points.PointAccountingService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs the actual packaged service in a disposable MySQL schema, without Spring Boot or model calls. */
public class PointAccountConcurrencyProbe {
    private static JdbcTemplate jdbc;
    private static PointAccountingService accounts;
    private static TransactionTemplate transactions;

    public static void main(String[] args) throws Exception {
        var source = new DriverManagerDataSource(System.getenv("POINT_ACCOUNT_MYSQL_URL"),
            "root", System.getenv("POINT_ACCOUNT_MYSQL_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        accounts = new PointAccountingService(jdbc, null);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transactions.setTimeout(15);
        jdbc.execute("""
            create table team_point_account (
              id bigint auto_increment primary key, tenant_id bigint not null,
              balance decimal(24,8) not null, total_granted decimal(24,8) not null,
              total_consumed decimal(24,8) not null, reserved_balance decimal(24,8) not null default 0,
              total_reserved decimal(24,8) not null default 0, total_released decimal(24,8) not null default 0,
              total_refunded decimal(24,8) not null default 0, version int not null default 0,
              trial_balance decimal(24,8) not null default 0, created_at datetime not null,
              updated_at datetime not null, unique key uk_team_point_account_tenant(tenant_id)
            ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
            """);
        System.out.println("MySQL=" + jdbc.queryForObject("select version()", String.class));
        for (long tenant = 100; tenant < 120; tenant++) {
            long current = tenant;
            transactions.executeWithoutResult(status -> accounts.ensureAccount(current));
            var gate = new CyclicBarrier(2);
            parallel(2, () -> {
                transactions.executeWithoutResult(status -> {
                    accounts.ensureAccount(current);
                    await(gate);
                    accounts.lockAccount(current);
                    jdbc.update("update team_point_account set balance=balance+1 where tenant_id=?", current);
                });
                return null;
            });
            require(balance(current).compareTo(new BigDecimal("2")) == 0, "Existing-account updates lost");
        }
        System.out.println("PASS existing-account lock upgrade regression: 20 rounds, 40 transactions");
        for (long tenant = 200; tenant < 220; tenant++) {
            long current = tenant;
            var gate = new CyclicBarrier(8);
            parallel(8, () -> {
                await(gate);
                transactions.executeWithoutResult(status -> {
                    accounts.lockAccount(current);
                    jdbc.update("update team_point_account set balance=balance+1 where tenant_id=?", current);
                });
                return null;
            });
            require(jdbc.queryForObject("select count(*) from team_point_account where tenant_id=?",
                Integer.class, current) == 1, "Duplicate account");
            require(balance(current).compareTo(new BigDecimal("8")) == 0, "Concurrent creation overwrote funds");
        }
        System.out.println("PASS concurrent first-use: 20 rounds, 160 transactions, one account per tenant");
        var worker = Executors.newSingleThreadExecutor();
        try {
            transactions.executeWithoutResult(status -> {
                require(jdbc.queryForObject("select count(*) from team_point_account where tenant_id=300", Integer.class) == 0,
                    "Snapshot fixture already exists");
                try {
                    worker.submit(() -> transactions.executeWithoutResult(other -> {
                        accounts.ensureAccount(300L);
                        jdbc.update("update team_point_account set balance=77,trial_balance=7,version=9 where tenant_id=300");
                    })).get(15, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                accounts.ensureAccount(300L);
                accounts.lockAccount(300L);
                var row = jdbc.queryForMap("select balance,trial_balance,version from team_point_account where tenant_id=300 for update");
                require(new BigDecimal("77").compareTo((BigDecimal)row.get("balance")) == 0, "Stale snapshot overwrote balance");
                require(new BigDecimal("7").compareTo((BigDecimal)row.get("trial_balance")) == 0, "Stale snapshot overwrote trial funds");
                require(((Number)row.get("version")).intValue() == 9, "Initialization changed version");
            });
        } finally {
            worker.shutdownNow();
        }
        System.out.println("PASS stale repeatable-read snapshot: concurrent creator's balance, trial funds and version preserved");
        System.out.println("ALL ACCOUNT CONCURRENCY PROBES PASSED");
    }

    private static BigDecimal balance(long tenant) {
        return jdbc.queryForObject("select balance from team_point_account where tenant_id=?", BigDecimal.class, tenant);
    }

    private static void parallel(int count, Callable<Void> action) throws Exception {
        var pool = Executors.newFixedThreadPool(count);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<Void>>();
            for (int index = 0; index < count; index++) futures.add(pool.submit(action));
            for (var future : futures) future.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(20, TimeUnit.SECONDS);
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
