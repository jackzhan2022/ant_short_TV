package com.antshorttv.operations.alerts;

import com.antshorttv.operations.OperationsDatabase;
import com.antshorttv.operations.OperationsTime;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;

/** Short database transactions protect all publications with a monotonically increasing fence. */
public final class OperationsLease {
    public record Lease(String job, String owner, long token) {}
    private final OperationsDatabase database;
    public OperationsLease(OperationsDatabase database) { this.database = database; }

    public Lease acquire(String job, String owner, Instant now, Duration duration) {
        if (duration.isNegative() || duration.isZero()) throw new IllegalArgumentException("Positive lease duration required");
        try {
            database.jdbc().update("insert into ops_job_lease(job_key,owner_id,fencing_token,expires_at,updated_at) values(?,?,0,?,?)",
                job,"",Timestamp.from(Instant.EPOCH),Timestamp.from(now));
        } catch (DuplicateKeyException ignored) { /* The row is intentionally permanent. */ }
        return database.transactions().execute(tx -> {
            var rows = database.jdbc().queryForList("select owner_id,fencing_token,expires_at from ops_job_lease where job_key=? for update",job);
            var row=rows.get(0);
            if (OperationsTime.utc(row.get("expires_at")).isAfter(now)) return null;
            long token=((Number)row.get("fencing_token")).longValue()+1;
            database.jdbc().update("update ops_job_lease set owner_id=?,fencing_token=?,expires_at=?,updated_at=? where job_key=?",
                owner,token,Timestamp.from(now.plus(duration)),Timestamp.from(now),job);
            return new Lease(job,owner,token);
        });
    }
    public <T> T fenced(Lease lease, Instant now, Supplier<T> work) {
        long started=System.nanoTime();
        return database.transactions().execute(tx -> {
            var rows=database.jdbc().queryForList("select owner_id,fencing_token,expires_at from ops_job_lease where job_key=? for update",lease.job());
            if (rows.isEmpty()) throw new IllegalStateException("Operations lease lost");
            var row=rows.get(0);
            Instant expires=OperationsTime.utc(row.get("expires_at"));
            if (!Objects.equals(row.get("owner_id"),lease.owner()) || ((Number)row.get("fencing_token")).longValue()!=lease.token()
                || !expires.isAfter(now)) throw new IllegalStateException("Operations lease lost");
            T result=work.get();
            if (!expires.isAfter(now.plusNanos(System.nanoTime()-started))) throw new IllegalStateException("Operations lease expired before publication");
            return result;
        });
    }
    public void release(Lease lease, Instant now) {
        database.jdbc().update("update ops_job_lease set expires_at=?,updated_at=? where job_key=? and owner_id=? and fencing_token=?",
            Timestamp.from(Instant.EPOCH),Timestamp.from(now),lease.job(),lease.owner(),lease.token());
    }
}
