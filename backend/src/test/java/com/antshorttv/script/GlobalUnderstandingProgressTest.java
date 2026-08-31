package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.common.ErrorCode;
import org.junit.jupiter.api.Test;

class GlobalUnderstandingProgressTest {
    @Test
    void mapsEveryDurableAgentProgressState() {
        assertThat(GlobalUnderstandingProgress.waiting().action()).contains("等待");
        assertThat(GlobalUnderstandingProgress.reading().percent()).isEqualTo(20);
        assertThat(GlobalUnderstandingProgress.analyzing().percent()).isEqualTo(40);
        assertThat(GlobalUnderstandingProgress.saving().percent()).isEqualTo(80);
        assertThat(GlobalUnderstandingProgress.committed().percent()).isEqualTo(100);
        assertThat(GlobalUnderstandingProgress.failed(ErrorCode.SCRIPT_CONTENT_CHANGED).action())
            .contains("剧本已变化");
        assertThat(GlobalUnderstandingProgress.failed(ErrorCode.WORKFLOW_AGENT_TOOL_INVALID).action())
            .contains("校验失败");
        assertThat(GlobalUnderstandingProgress.retrying().action()).contains("重试");
    }
}
