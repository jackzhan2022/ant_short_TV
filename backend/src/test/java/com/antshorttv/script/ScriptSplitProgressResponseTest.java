package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ScriptSplitProgressResponseTest {
    @Test
    void attachesFallbackProgressOnlyToTheSplittingStage() {
        ScriptAnalysisStageEntity stage = new ScriptAnalysisStageEntity();
        stage.setId(8L);
        stage.setStageCode("EPISODE_SPLITTING");
        EpisodeSplitProgressResponse progress = new EpisodeSplitProgressResponse(
            "CHUNK_FALLBACK", "OUTPUT_TRUNCATED", 12, 7, 1, false);

        ScriptAnalysisStageResponse response = ScriptAnalysisStageResponse.from(
            stage, null, 91L, null, progress);

        assertThat(response.splitProgress()).isEqualTo(progress);
        assertThat(response.fanout()).isNull();
    }
}
