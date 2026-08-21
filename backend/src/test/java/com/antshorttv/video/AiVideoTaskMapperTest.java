package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiVideoTaskMapperTest {

    @Test
    void listQueryColumnsDoNotRequireExecutionReliabilityMigration() {
        assertThat(AiVideoTaskMapper.LIST_QUERY_COLUMNS)
            .doesNotContain(
                "execution_token",
                "execution_phase",
                "execution_version",
                "claimed_at",
                "heartbeat_at",
                "execution_timeout_at",
                "retryable"
            );
    }
}
