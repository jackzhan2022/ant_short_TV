package com.antshorttv.operations.alerts;

import static org.assertj.core.api.Assertions.*;
import com.antshorttv.operations.OperationsDatabase;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class OperationsLeaseTest {
    @Test void mysqlLocalDateTimeResultShapeSupportsAcquireAndFencing() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:mysql-shape"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(ds){
            @Override public java.util.List<java.util.Map<String,Object>> queryForList(String sql,Object...args) {
                var rows=super.queryForList(sql,args);
                rows.forEach(row->row.replaceAll((key,value)->value instanceof java.sql.Timestamp timestamp
                    ?java.time.LocalDateTime.ofInstant(timestamp.toInstant(),ZoneOffset.UTC):value));
                return rows;
            }
        };
        jdbc.execute("create table ops_job_lease(job_key varchar(160) primary key,owner_id varchar(160),fencing_token bigint,expires_at timestamp(3),updated_at timestamp(3))");
        var lease=new OperationsLease(new OperationsDatabase(jdbc));Instant now=Instant.parse("2026-11-05T01:00:00Z");
        var owner=lease.acquire("job","instance",now,Duration.ofSeconds(30));
        assertThat(lease.fenced(owner,now.plusSeconds(1),()->"published")).isEqualTo("published");
    }
    @Test void workThatOutlivesItsLeaseRollsBackBeforeAnotherOwnerPublishes() {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:expiry"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("create table ops_job_lease(job_key varchar(160) primary key,owner_id varchar(160),fencing_token bigint,expires_at timestamp(3),updated_at timestamp(3))");
        jdbc.execute("create table publication(id int primary key)");
        var coordinator=new OperationsLease(new OperationsDatabase(jdbc));
        Instant now=Instant.parse("2026-11-05T01:00:00Z");
        var first=coordinator.acquire("evaluate","one",now,Duration.ofMillis(20));
        assertThatThrownBy(()->coordinator.fenced(first,now,()->{
            jdbc.update("insert into publication values(1)");
            try{Thread.sleep(25);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
            return true;
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from publication",Integer.class)).isZero();
        var second=coordinator.acquire("evaluate","two",now.plusMillis(50),Duration.ofSeconds(1));
        coordinator.fenced(second,now.plusMillis(51),()->jdbc.update("insert into publication values(2)"));
        assertThat(jdbc.queryForList("select id from publication",Integer.class)).containsExactly(2);
    }
    @Test void staleOwnerCannotPublishAfterAnotherOwnerTakesExpiredLease() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:lease" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("create table ops_job_lease(job_key varchar(160) primary key,owner_id varchar(160),fencing_token bigint,expires_at timestamp(3),updated_at timestamp(3))");
        var lease = new OperationsLease(new OperationsDatabase(jdbc));
        Instant now = Instant.parse("2026-11-05T01:00:00Z");
        var first = lease.acquire("evaluate","one",now,Duration.ofSeconds(30));
        assertThat(lease.acquire("evaluate","two",now,Duration.ofSeconds(30))).isNull();
        var second = lease.acquire("evaluate","two",now.plusSeconds(31),Duration.ofSeconds(30));
        assertThat(second.token()).isGreaterThan(first.token());
        assertThatThrownBy(() -> lease.fenced(first,now.plusSeconds(31),() -> "bad")).isInstanceOf(IllegalStateException.class);
        assertThat(lease.fenced(second,now.plusSeconds(32),() -> "good")).isEqualTo("good");
    }
}
