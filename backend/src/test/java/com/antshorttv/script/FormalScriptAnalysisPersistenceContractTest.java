package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableName;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FormalScriptAnalysisPersistenceContractTest {
    @Autowired private org.springframework.context.ApplicationContext context;

    @Test
    void exposesFormalSummaryPersistenceAndCurrentEpisodeMetadata() {
        assertThat(ScriptEpisodeSummaryEntity.class.getAnnotation(TableName.class).value())
            .isEqualTo("script_episode_summary");
        assertThat(context.getBean(ScriptEpisodeSummaryMapper.class)).isNotNull();
        assertThat(context.getBean(ScriptEpisodeSummaryRepository.class)).isNotNull();
        assertThat(recordFields(ScriptEpisodeSummaryDocument.class)).containsExactly(
            "id", "tenantId", "projectId", "scriptId", "episodeId", "schemaVersion",
            "content", "source", "generatedByRunId", "createdBy", "updatedBy", "createdAt", "updatedAt");
        assertThat(recordFields(ScriptEpisodeResponse.class)).contains(
            "contentFingerprint", "generatedByRunId", "formalSummary");
    }

    @Test
    void mapsExtensibleAgentMetadataAndRunProvenance() throws Exception {
        assertThat(ScriptEpisodeEntity.class.getDeclaredField("generatedByRunId")).isNotNull();
        assertThat(AssetVisualVariantEntity.class.getDeclaredField("contentJson")).isNotNull();
        assertThat(AssetVisualVariantEntity.class.getDeclaredField("generatedByRunId")).isNotNull();
        assertThat(AssetVisualVariantEpisodeEntity.class.getDeclaredField("generatedByRunId")).isNotNull();
        assertThat(recordFields(ScriptAssetMetadata.class)).containsExactly(
            "scriptId", "normalizedName", "content", "source", "generatedByRunId");
    }

    private static java.util.List<String> recordFields(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}
