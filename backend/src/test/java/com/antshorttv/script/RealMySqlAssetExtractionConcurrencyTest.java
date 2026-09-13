package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "ASSET_TEST_MYSQL_URL", matches = "jdbc:mysql:.*")
class RealMySqlAssetExtractionConcurrencyTest {
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static String coordinationTable;
    private static String operationTable;
    private static String executionTable;
    private static String reservationTable;

    @BeforeAll
    static void setUpDatabase() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        coordinationTable = "asset_test_coord_" + suffix;
        operationTable = "asset_test_operation_" + suffix;
        executionTable = "asset_test_execution_" + suffix;
        reservationTable = "asset_test_reservation_" + suffix;
        var source = new DriverManagerDataSource(
            System.getenv("ASSET_TEST_MYSQL_URL"), System.getenv("ASSET_TEST_MYSQL_USERNAME"),
            System.getenv("ASSET_TEST_MYSQL_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.execute("create table " + coordinationTable + " " + """
            (
              id bigint primary key auto_increment, tenant_id bigint not null, project_id bigint not null,
              script_id bigint not null, owner_execution_id bigint null, owner_execution_version int null,
              owner_attempt bigint null, source_fingerprint varchar(128), state varchar(32) not null,
              created_at datetime not null, updated_at datetime not null, released_at datetime null,
              unique key uk_coordination (tenant_id, project_id, script_id)) engine=InnoDB
            """);
        jdbc.execute("create table " + operationTable + " " + """
            (
              id bigint primary key auto_increment, tenant_id bigint not null, client_key varchar(128) not null)
              engine=InnoDB
            """);
        jdbc.execute("create table " + executionTable + " " + """
            (
              id bigint primary key auto_increment, operation_id bigint not null, user_id bigint not null,
              status varchar(32) not null) engine=InnoDB
            """);
        jdbc.execute("create table " + reservationTable + " " + """
            (
              id bigint primary key auto_increment, execution_id bigint not null, user_id bigint not null)
              engine=InnoDB
            """);
    }

    @AfterAll
    static void dropDatabase() {
        if (jdbc == null) return;
        for (String table : new String[]{reservationTable, executionTable, operationTable, coordinationTable}) {
            if (table != null) jdbc.execute("drop table if exists " + table);
        }
    }

    @Test
    void coordinatesBillingAndOwnershipAcrossRealTransactions() {
        CountDownLatch start = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> admit(7, 8, 9, 101,
            "source:ALL:FILL:model:user101", "client-a", start));
        var duplicate = CompletableFuture.supplyAsync(() -> admit(7, 8, 9, 101,
            "source:ALL:FILL:model:user101", "client-b", start));
        start.countDown();

        AdmissionResult a = first.join();
        AdmissionResult b = duplicate.join();
        assertThat(a.executionId()).isEqualTo(b.executionId());
        assertThat(jdbc.queryForObject("select count(*) from " + operationTable, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from " + executionTable, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from " + reservationTable, Integer.class)).isEqualTo(1);

        assertThat(admit(7, 8, 9, 202, "source:ALL:FILL:model:user202", "other-user", null).kind())
            .isEqualTo("CONFLICT");
        assertThat(admit(7, 8, 9, 101, "analysis:source", "analysis-entry", null).kind())
            .isEqualTo("CONFLICT");

        CountDownLatch scriptsStart = new CountDownLatch(1);
        var scriptTen = CompletableFuture.supplyAsync(() -> admit(7, 8, 10, 101,
            "source-10", "script-10", scriptsStart));
        var scriptEleven = CompletableFuture.supplyAsync(() -> admit(7, 8, 11, 101,
            "source-11", "script-11", scriptsStart));
        scriptsStart.countDown();
        assertThat(scriptTen.join().kind()).isEqualTo("ACQUIRED");
        assertThat(scriptEleven.join().kind()).isEqualTo("ACQUIRED");
    }

    private AdmissionResult admit(long tenantId, long projectId, long scriptId, long userId,
                                  String fingerprint, String clientKey, CountDownLatch start) {
        if (start != null) {
            try {
                start.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
        return transactions.execute(status -> {
            boolean acquired = false;
            try {
                jdbc.update("insert into " + coordinationTable
                    + " (tenant_id,project_id,script_id,source_fingerprint,state,created_at,updated_at)"
                    + " values (?,?,?,?,'OWNED',now(),now())", tenantId, projectId, scriptId, fingerprint);
                acquired = true;
            } catch (DuplicateKeyException ignored) {
                // The unique script row serializes both clients on real InnoDB transactions.
            }
            if (!acquired) {
                var row = jdbc.queryForMap("select owner_execution_id,source_fingerprint from "
                    + coordinationTable + " where tenant_id=? and project_id=? and script_id=? for update",
                    tenantId, projectId, scriptId);
                Long owner = ((Number) row.get("owner_execution_id")).longValue();
                return new AdmissionResult(
                    fingerprint.equals(row.get("source_fingerprint")) ? "REUSED" : "CONFLICT", owner);
            }
            jdbc.update("insert into " + operationTable + "(tenant_id,client_key) values (?,?)", tenantId, clientKey);
            Long operationId = jdbc.queryForObject("select last_insert_id()", Long.class);
            jdbc.update("insert into " + executionTable + "(operation_id,user_id,status) values (?,?,'PENDING')",
                operationId, userId);
            Long executionId = jdbc.queryForObject("select last_insert_id()", Long.class);
            jdbc.update("insert into " + reservationTable + "(execution_id,user_id) values (?,?)", executionId, userId);
            assertThat(jdbc.update("update " + coordinationTable
                + " set owner_execution_id=?,owner_execution_version=1,owner_attempt=0,updated_at=now()"
                + " where tenant_id=? and project_id=? and script_id=? and owner_execution_id is null",
                executionId, tenantId, projectId, scriptId)).isEqualTo(1);
            return new AdmissionResult("ACQUIRED", executionId);
        });
    }

    private record AdmissionResult(String kind, Long executionId) {}
}
