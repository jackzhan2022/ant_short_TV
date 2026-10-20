package com.antshorttv.execution;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import jakarta.validation.Validator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AiConcurrencyConfigService {
    private static final long CACHE_NANOS = TimeUnit.SECONDS.toNanos(1);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final TransactionTemplate committedReads;
    private final Validator validator;
    private final AiConcurrencySettings defaults;
    private final String admissionLockClause;
    private AiConcurrencySettings cached;
    private long cachedAt;

    public AiConcurrencyConfigService(
        JdbcTemplate jdbc,
        PlatformTransactionManager transactionManager,
        Environment environment,
        Validator validator
    ) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.committedReads = new TransactionTemplate(transactionManager);
        this.committedReads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.committedReads.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.committedReads.setReadOnly(true);
        this.validator = validator;
        int tenantLimit = property(environment, "ai.execution.max-concurrent-per-tenant", 5);
        int workerThreads = property(environment, "ai.execution.worker-threads", 4);
        int textWorkers = Math.max(1, workerThreads / 2);
        int imageWorkers = Math.max(1, workerThreads - textWorkers);
        this.defaults = new AiConcurrencySettings(tenantLimit, tenantLimit,
            property(environment, "ai.video.max-concurrent-per-tenant", 3),
            property(environment, "ai.execution.max-concurrent-per-model", 4),
            property(environment, "ai.provider.max-concurrency", 4),
            textWorkers, imageWorkers, 1, 1);
        String databaseProduct = jdbc.execute((ConnectionCallback<String>) connection ->
            connection.getMetaData().getDatabaseProductName());
        this.admissionLockClause = "H2".equalsIgnoreCase(databaseProduct) ? " for update" : " for share";
    }

    /** A process-local snapshot; other instances' committed changes are reloaded within one second. */
    public synchronized AiConcurrencySettings current() {
        long now = System.nanoTime();
        if (cached == null || now - cachedAt >= CACHE_NANOS) {
            // A claim or caller's transaction must not expose uncommitted settings or pin a
            // repeatable-read snapshot past the cache TTL. Suspend it for a committed refresh.
            cached = TransactionSynchronizationManager.isActualTransactionActive()
                ? committedReads.execute(status -> read(false)) : read(false);
            cachedAt = now;
        }
        return cached;
    }

    /**
     * Read uncached settings and hold the configuration row lock until the caller's transaction
     * ends. Call before taking tenant/model locks. Never acquire the cache monitor on this path:
     * save() may hold it while waiting for this row. MySQL admissions share the lock; H2 uses
     * its supported FOR UPDATE syntax for tests.
     */
    public AiConcurrencySettings lockForAdmission() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("AI admission settings require an active transaction");
        }
        return jdbc.queryForObject("select * from ai_concurrency_config where id=1" + admissionLockClause,
            (row, index) -> settings(row));
    }

    public synchronized AiConcurrencySettings save(AiConcurrencySettings settings) {
        if (settings == null || !validator.validate(settings).isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "并发上限须为 1–10000，工作线程数须为 1–200。");
        }
        try {
            transactions.executeWithoutResult(status -> {
                // The seeded singleton row serializes saves across application instances.
                AiConcurrencySettings previous = read(true);
                jdbc.update("""
                    update ai_concurrency_config set team_text_limit=?, team_image_limit=?, team_video_limit=?,
                        model_limit=?, provider_limit=?, text_workers=?, image_workers=?,
                        video_submit_workers=?, video_query_workers=?, updated_at=current_timestamp
                    where id=1
                    """, settings.teamTextLimit(), settings.teamImageLimit(), settings.teamVideoLimit(),
                    settings.modelLimit(), settings.providerLimit(), settings.textWorkers(), settings.imageWorkers(),
                    settings.videoSubmitWorkers(), settings.videoQueryWorkers());
                if (previous.modelLimit() != settings.modelLimit()) {
                    jdbc.update("""
                        update ai_model_execution_quota set concurrency_limit=?, updated_at=current_timestamp
                        """, settings.modelLimit());
                }
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int completionStatus) {
                        invalidateCache();
                    }
                });
            });
            return settings;
        } finally {
            cached = null;
        }
    }

    private synchronized void invalidateCache() {
        cached = null;
    }

    private AiConcurrencySettings read(boolean lock) {
        return jdbc.queryForObject("select * from ai_concurrency_config where id=1" + (lock ? " for update" : ""),
            (row, index) -> settings(row));
    }

    private AiConcurrencySettings settings(ResultSet row) throws SQLException {
        if (row.getObject("team_text_limit") == null) {
            return defaults;
        }
        return new AiConcurrencySettings(row.getInt("team_text_limit"), row.getInt("team_image_limit"),
            row.getInt("team_video_limit"), row.getInt("model_limit"), row.getInt("provider_limit"),
            row.getInt("text_workers"), row.getInt("image_workers"), row.getInt("video_submit_workers"),
            row.getInt("video_query_workers"));
    }

    private static int property(Environment environment, String key, int fallback) {
        return Math.max(1, environment.getProperty(key, Integer.class, fallback));
    }
}
