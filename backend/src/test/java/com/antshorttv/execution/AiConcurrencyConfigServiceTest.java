package com.antshorttv.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;

class AiConcurrencyConfigServiceTest {
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private ValidatorFactory validators;
    private MockEnvironment environment;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        transactions = new DataSourceTransactionManager(dataSource);
        validators = Validation.buildDefaultValidatorFactory();
        environment = new MockEnvironment();
        // Minimal pre-migration schema also exercises the actual MySQL-compatible migration.
        jdbc.execute("create table ai_execution_task(id bigint, tenant_id bigint, "
            + "capability varchar(64), scene varchar(64), business_type varchar(64), status varchar(32), "
            + "next_run_at datetime, priority int, created_at datetime, claim_expires_at datetime)");
        jdbc.execute("create table ai_video_task(id bigint, status varchar(32), next_poll_at datetime)");
        jdbc.execute("create table platform_permission(id bigint auto_increment primary key, code varchar(128), "
            + "name varchar(128), resource varchar(64), action varchar(64), created_at datetime, updated_at datetime)");
        jdbc.execute("create table platform_role(id bigint, code varchar(64))");
        jdbc.execute("create table platform_role_permission(role_id bigint, permission_id bigint, created_at datetime)");
        jdbc.execute("create table ai_model_execution_quota(model_id bigint primary key, concurrency_limit int, updated_at datetime)");
        jdbc.update("insert into platform_role values(1, 'PLATFORM_ADMIN'), (2, 'TENANT_MEMBER')");
        jdbc.update("insert into ai_execution_task(id,capability,scene,business_type) values "
            + "(1,'IMAGE','other','other'), (2,'TEXT','ai_image_generate','other'), "
            + "(3,'VIDEO','other','other'), (4,'TEXT','other','AI_VIDEO'), "
            + "(5,'VIDEO_UNDERSTANDING','video_decomposition','VIDEO_DECOMPOSITION'), "
            + "(6,'TEXT','script_generate','SCRIPT_OPERATION')");
        new ResourceDatabasePopulator(new ClassPathResource(
            "db/migration/V141__ai_concurrency_configuration.sql")).execute(dataSource);
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("drop all objects");
        validators.close();
    }

    private AiConcurrencyConfigService service() {
        return new AiConcurrencyConfigService(jdbc, transactions, environment, validators.getValidator());
    }

    @Test
    void defaultsHonorExistingEnvironmentUntilSavedAndSurviveNewServiceInstances() {
        assertThat(service().current()).isEqualTo(new AiConcurrencySettings(5, 5, 3, 4, 4, 2, 2, 1, 1));
        environment.withProperty("ai.execution.max-concurrent-per-tenant", "2")
            .withProperty("ai.video.max-concurrent-per-tenant", "1")
            .withProperty("ai.execution.max-concurrent-per-model", "3")
            .withProperty("ai.provider.max-concurrency", "2")
            .withProperty("ai.execution.worker-threads", "3");
        var first = service();
        var defaults = first.current();
        assertThat(defaults).isEqualTo(new AiConcurrencySettings(2, 2, 1, 3, 2, 1, 2, 1, 1));
        var saved = new AiConcurrencySettings(1, 3, 2, 5, 1, 3, 1, 2, 4);
        assertThat(first.save(saved)).isEqualTo(saved);
        assertThat(first.current()).isEqualTo(saved);
        environment.withProperty("ai.provider.max-concurrency", "9");
        assertThat(service().current()).isEqualTo(saved);
    }

    @Test
    void observesOtherInstanceSaveWithinOneSecond() throws Exception {
        var reader = service();
        reader.current();
        var saved = new AiConcurrencySettings(1, 2, 3, 4, 5, 6, 7, 8, 9);
        service().save(saved);
        Thread.sleep(1050);
        assertThat(reader.current()).isEqualTo(saved);
    }

    @Test
    void refreshInsideLongTransactionDoesNotReuseItsRepeatableReadSnapshot() throws Exception {
        var reader = service();
        var writer = service();
        var saved = new AiConcurrencySettings(1, 2, 3, 4, 5, 6, 7, 8, 9);
        var executor = Executors.newSingleThreadExecutor();
        var transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try {
            transaction.executeWithoutResult(status -> {
                jdbc.queryForObject("select team_text_limit from ai_concurrency_config where id=1", Integer.class);
                reader.current();
                try {
                    executor.submit(() -> writer.save(saved)).get(3, TimeUnit.SECONDS);
                    Thread.sleep(1050);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                assertThat(reader.current()).isEqualTo(saved);
            });
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void updatesModelQuotaOnlyWhenGlobalDefaultChanges() {
        jdbc.update("insert into ai_model_execution_quota values(11, 9, now()), (12, 2, now())");
        var service = service();
        service.save(new AiConcurrencySettings(5, 5, 3, 4, 2, 2, 2, 1, 1));
        assertThat(jdbc.queryForList("select concurrency_limit from ai_model_execution_quota order by model_id", Integer.class))
            .containsExactly(9, 2);
        service.save(new AiConcurrencySettings(5, 5, 3, 1, 2, 2, 2, 1, 1));
        assertThat(jdbc.queryForList("select concurrency_limit from ai_model_execution_quota", Integer.class))
            .containsOnly(1);
    }

    @Test
    void admissionLockRequiresAnActiveTransaction() {
        assertThatThrownBy(() -> service().lockForAdmission())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void saveWaitsForAdmissionToCommitAndUpdatesItsNewModelQuota() throws Exception {
        var config = service();
        var saved = new AiConcurrencySettings(5, 5, 3, 2, 4, 2, 2, 1, 1);
        var admissionLocked = new CountDownLatch(1);
        var finishAdmission = new CountDownLatch(1);
        var saveStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var admission = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                var settings = config.lockForAdmission();
                assertThat(settings.modelLimit()).isEqualTo(4);
                admissionLocked.countDown();
                try {
                    assertThat(finishAdmission.await(3, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                // save() can now hold the Java monitor while waiting for this database lock.
                // Admission must read directly without trying to acquire that monitor.
                assertThat(config.lockForAdmission()).isEqualTo(settings);
                jdbc.update("insert into ai_model_execution_quota values(11, ?, now())", settings.modelLimit());
            }));
            assertThat(admissionLocked.await(2, TimeUnit.SECONDS)).isTrue();
            var save = executor.submit(() -> {
                saveStarted.countDown();
                return config.save(saved);
            });
            assertThat(saveStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> save.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            finishAdmission.countDown();
            admission.get(3, TimeUnit.SECONDS);
            assertThat(save.get(3, TimeUnit.SECONDS)).isEqualTo(saved);
            assertThat(jdbc.queryForObject("select concurrency_limit from ai_model_execution_quota where model_id=11",
                Integer.class)).isEqualTo(2);
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                assertThat(config.lockForAdmission()).isEqualTo(saved);
            });
        } finally {
            finishAdmission.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void admissionReadBypassesCachedSettingsFromAnotherInstance() {
        var reader = service();
        reader.current();
        var saved = new AiConcurrencySettings(1, 2, 3, 2, 1, 1, 1, 1, 1);
        service().save(saved);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(reader.lockForAdmission()).isEqualTo(saved);
        });
    }

    @Test
    void rejectsEveryOutOfBoundsFieldWithoutChangingDatabase() {
        var service = service();
        var original = service.save(service.current());
        for (int field = 0; field < 9; field++) {
            for (int bad : new int[]{0, -1, field < 5 ? 10001 : 201}) {
                int[] values = {5, 5, 3, 4, 4, 2, 2, 1, 1};
                values[field] = bad;
                var invalid = new AiConcurrencySettings(values[0], values[1], values[2], values[3], values[4],
                    values[5], values[6], values[7], values[8]);
                assertThatThrownBy(() -> service.save(invalid)).isInstanceOf(BusinessException.class);
                assertThat(service().current()).isEqualTo(original);
            }
        }
        assertThatThrownBy(() -> service.save(null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void settingsAndModelQuotasRollBackTogetherAndDoNotLeakIntoCache() {
        var service = service();
        var original = service.current();
        jdbc.update("insert into ai_model_execution_quota values(11, 9, now())");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.save(new AiConcurrencySettings(1, 1, 1, 2, 1, 1, 1, 1, 1));
            assertThat(service.current()).isEqualTo(original);
            status.setRollbackOnly();
        });
        assertThat(service.current()).isEqualTo(original);
        assertThat(jdbc.queryForObject("select concurrency_limit from ai_model_execution_quota", Integer.class)).isEqualTo(9);
    }

    @Test
    void quotaWriteFailureRollsBackSettingsUpdate() {
        var service = service();
        var original = service.current();
        jdbc.update("insert into ai_model_execution_quota values(11, 9, now())");
        jdbc.execute("alter table ai_model_execution_quota add constraint test_quota_minimum check (concurrency_limit >= 3)");
        assertThatThrownBy(() -> service.save(new AiConcurrencySettings(1, 1, 1, 2, 1, 1, 1, 1, 1)))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(service.current()).isEqualTo(original);
        assertThat(jdbc.queryForObject("select concurrency_limit from ai_model_execution_quota", Integer.class)).isEqualTo(9);
    }

    @Test
    void migrationBackfillsTypesAndGrantsOnlyPlatformAdmin() {
        assertThat(jdbc.queryForList("select task_type from ai_execution_task order by id", String.class))
            .containsExactly("IMAGE", "IMAGE", "VIDEO", "VIDEO", "TEXT", "TEXT");
        assertThat(jdbc.queryForList("select distinct role_id from platform_role_permission", Long.class))
            .containsExactly(1L);
        assertThat(jdbc.queryForObject("select count(*) from platform_role_permission", Integer.class)).isEqualTo(2);
        jdbc.update("insert into ai_execution_tenant_quota(tenant_id,task_type) values (7,'TEXT'), (7,'IMAGE')");
    }
}
