package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ReviewObservabilityRepositoryTest {

    @Test
    void preservesKnownCachedTokensWhenCacheWriteDetailIsUnknown() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:review_cache_partial;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("create table ai_call_log (id bigint primary key, prompt_tokens bigint, completion_tokens bigint, duration_ms bigint, cached_input_tokens bigint, cache_write_tokens bigint)");
        jdbc.execute("create table ai_workflow_agent_run_step (id bigint primary key, run_id bigint, ai_call_log_id bigint)");
        jdbc.update("insert into ai_call_log values (1, 100, 20, 250, 80, null)");
        jdbc.update("insert into ai_workflow_agent_run_step values (1, 9, 1)");

        ReviewCacheUsageResponse usage = new ReviewObservabilityRepository(jdbc)
            .load(List.of(9L)).cacheUsage();

        assertThat(usage.cachedInputTokens()).isEqualTo(80L);
        assertThat(usage.cacheWriteTokens()).isNull();
        assertThat(usage.ordinaryInputTokens()).isNull();
        assertThat(usage.cacheHitRatio()).isEqualByComparingTo(new BigDecimal("0.8000"));
        assertThat(usage.cacheObservable()).isTrue();
    }
}
