package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.review.ReviewTaskEntity;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ScriptDomainExecutionLinkContractTest {

    @Test
    void complexScriptDomainTasksMapTheirSharedExecutionIdentity() {
        assertExecutionField(ScriptAnalysisTaskEntity.class);
        assertExecutionField(ScriptAnalysisResultEntity.class);
        assertExecutionField(ScriptVersionEntity.class);
        assertExecutionField(ReviewTaskEntity.class);
    }

    private void assertExecutionField(Class<?> entityType) {
        assertThat(Arrays.stream(entityType.getDeclaredFields()).map(Field::getName))
            .as(entityType.getSimpleName() + " execution correlation")
            .contains("executionId");
    }
}
