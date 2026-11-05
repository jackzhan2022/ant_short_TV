package com.antshorttv.operations;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class OperationsCoverageServiceTest {
    OperationsCoverageService coverage;
    Instant end=Instant.parse("2026-11-05T01:10:00Z");
    @BeforeEach void setup() {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:coverage"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("create table ops_runtime_setting(setting_key varchar(160) primary key,value_json text not null,version bigint not null,updated_at timestamp(3) not null)");
        coverage=new OperationsCoverageService(new OperationsDatabase(jdbc),new ObjectMapper());
    }
    @Test void resourcePresenceDoesNotCertifyHttpOrAiAndExplicitFamilyFinalizationDoes() {
        coverage.recordRange("RESOURCE","one","boot",end.minusSeconds(600),end,end.plusSeconds(20));
        assertThat(coverage.complete("http.errorRate",Map.of(),5,end)).isFalse();
        assertThat(coverage.complete("ai.technicalFailureRate",Map.of(),5,end)).isFalse();
        coverage.recordRange("HTTP","one","boot",end.minusSeconds(600),end,end.plusSeconds(20));
        assertThat(coverage.complete("http.errorRate",Map.of(),5,end)).isTrue();
        coverage.recordRange("AI","GLOBAL","GLOBAL",end.minusSeconds(600),end,end.plusSeconds(20));
        assertThat(coverage.complete("ai.technicalFailureRate",Map.of("providerId",4L),5,end)).isTrue();
    }
    @Test void staleOrUnfinalizedSecondInstanceMakesGlobalWindowUnknownButExplicitScopeCanBeComplete() {
        coverage.recordRange("HTTP","two","boot-two",end.minusSeconds(600),end.minusSeconds(180),end.minusSeconds(160));
        coverage.recordRange("HTTP","one","boot-one",end.minusSeconds(600),end,end.plusSeconds(20));
        assertThat(coverage.complete("http.errorRate",Map.of(),5,end)).isFalse();
        assertThat(coverage.complete("http.errorRate",Map.of("instanceId","one"),5,end)).isTrue();
        assertThat(coverage.complete("http.errorRate",Map.of("instanceId","missing"),5,end)).isFalse();
    }
    @Test void knownHttpCollectorIsNotForgottenAfterMoreThanOneDayOffline() {
        Instant old=end.minusSeconds(90000);
        coverage.recordRange("HTTP","offline","boot-offline",old.minusSeconds(600),old,old.plusSeconds(20));
        coverage.recordRange("HTTP","live","boot-live",end.minusSeconds(600),end,end.plusSeconds(20));
        assertThat(coverage.complete("http.errorRate",Map.of(),5,end)).isFalse();
        assertThat(coverage.complete("http.errorRate",Map.of("instanceId","live"),5,end)).isTrue();
    }
    @Test void knownResourceCollectorIsNotForgottenAfterMoreThanOneDayOffline() {
        Instant old=end.minusSeconds(90000);
        coverage.recordRange("RESOURCE","offline","boot-offline",old.minusSeconds(600),old,old.plusSeconds(20));
        coverage.recordRange("RESOURCE","live","boot-live",end.minusSeconds(600),end,end.plusSeconds(20));
        coverage.recordRange("HTTP","live","boot-live",end.minusSeconds(600),end,end.plusSeconds(20));
        assertThat(coverage.complete("jvm.heapPercent",Map.of(),5,end)).isFalse();
        assertThat(coverage.complete("http.errorRate",Map.of(),5,end)).isFalse();
        assertThat(coverage.complete("jvm.heapPercent",Map.of("instanceId","live"),5,end)).isTrue();
    }
    @Test void overlappingCertifiedRangesMergeButMissingMinuteBreaksSustainedCoverage() {
        coverage.recordRange("HTTP","one","boot",end.minusSeconds(600),end.minusSeconds(300),end.minusSeconds(280));
        coverage.recordRange("HTTP","one","boot",end.minusSeconds(240),end,end.plusSeconds(20));
        assertThat(coverage.complete("http.errorRate",Map.of(),10,end)).isFalse();
        assertThat(coverage.complete("http.errorRate",Map.of(),4,end)).isTrue();
        coverage.recordRange("HTTP","one","boot",end.minusSeconds(240),end.plusSeconds(120),end.plusSeconds(140));
        assertThat(coverage.complete("http.errorRate",Map.of(),6,end.plusSeconds(120))).isTrue();
    }
    @Test void dropInvalidationCannotBeUndoneByReplayingOldCollectorRange() {
        coverage.recordRange("HTTP","one","boot",end.minusSeconds(600),end,end.plusSeconds(20));
        coverage.invalidate("HTTP","one","boot",end.plusSeconds(21));
        coverage.recordRange("HTTP","one","boot",end.minusSeconds(600),end.plusSeconds(120),end.plusSeconds(140));
        assertThat(coverage.complete("http.errorRate",Map.of(),2,end.plusSeconds(120))).isFalse();
        assertThat(coverage.complete("http.errorRate",Map.of(),1,end.plusSeconds(120))).isTrue();
    }
    @Test void futureMinuteAndOverlappingBootsCannotClaimFinalizedEvidence() {
        coverage.recordRange("HTTP","one","boot-one",end.minusSeconds(600),end.plusSeconds(60),end.plusSeconds(20));
        assertThat(coverage.complete("http.errorRate",Map.of(),1,end.plusSeconds(60))).isFalse();
        coverage.recordRange("HTTP","one","boot-two",end,end.plusSeconds(60),end.plusSeconds(80));
        assertThat(coverage.complete("http.errorRate",Map.of(),1,end.plusSeconds(60))).isFalse();
        coverage.recordRange("HTTP","one","boot-two",end,end.plusSeconds(240),end.plusSeconds(260));
        assertThat(coverage.complete("http.errorRate",Map.of(),1,end.plusSeconds(240))).isTrue();
    }
}
