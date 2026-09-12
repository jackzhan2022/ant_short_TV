package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ScriptAnalysisConfigSnapshotServiceTest {
    private final ScriptAnalysisConfigSnapshotMapper mapper = mock(ScriptAnalysisConfigSnapshotMapper.class);
    private final ScriptAnalysisConfigSnapshotService service = new ScriptAnalysisConfigSnapshotService(mapper, new ObjectMapper());

    @Test
    void firstSubmissionPersistsTheFirstAvailableModelBeforeDispatch() throws Exception {
        ScriptAnalysisTaskEntity task = new ScriptAnalysisTaskEntity();
        task.setId(41L);
        AtomicReference<ScriptAnalysisConfigSnapshotEntity> persisted = new AtomicReference<>();
        when(mapper.selectOne(any())).thenAnswer(invocation -> persisted.get());
        doAnswer(invocation -> {
            persisted.set(invocation.getArgument(0));
            return 1;
        }).when(mapper).insert(any(ScriptAnalysisConfigSnapshotEntity.class));
        @SuppressWarnings("unchecked")
        Supplier<Long> initialModel = mock(Supplier.class);
        when(initialModel.get()).thenReturn(12L);

        assertThat(service.modelIdForFirstSubmission(task, initialModel)).isEqualTo(12L);

        verify(initialModel).get();
        verify(mapper).insert(any(ScriptAnalysisConfigSnapshotEntity.class));
        assertThat(persisted.get().getTaskId()).isEqualTo(41L);
        assertThat(new ObjectMapper().readTree(persisted.get().getSnapshotJson()).path("modelId").longValue())
            .isEqualTo(12L);
        assertThat(task.getExecutionId()).isNull();
    }

    @Test
    void firstSubmissionUsesAnExistingSnapshotWithoutConsultingTheCurrentModel() {
        ScriptAnalysisTaskEntity task = new ScriptAnalysisTaskEntity();
        task.setId(41L);
        ScriptAnalysisConfigSnapshotEntity stored = new ScriptAnalysisConfigSnapshotEntity();
        stored.setSnapshotJson("{\"modelId\":12}");
        when(mapper.selectOne(any())).thenReturn(stored);
        @SuppressWarnings("unchecked")
        Supplier<Long> initialModel = mock(Supplier.class);

        assertThat(service.modelIdForFirstSubmission(task, initialModel)).isEqualTo(12L);

        verifyNoInteractions(initialModel);
        verify(mapper, never()).insert(any(ScriptAnalysisConfigSnapshotEntity.class));
    }

    @Test
    void dispatchedTaskWithAMissingSnapshotFailsWithoutSelectingAnotherModel() {
        ScriptAnalysisTaskEntity task = new ScriptAnalysisTaskEntity();
        task.setId(41L);
        task.setExecutionId(71L);
        @SuppressWarnings("unchecked")
        Supplier<Long> initialModel = mock(Supplier.class);

        assertThatThrownBy(() -> service.modelIdForFirstSubmission(task, initialModel))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("已派发").hasMessageContaining("快照缺失");

        verifyNoInteractions(initialModel);
        verify(mapper, never()).insert(any(ScriptAnalysisConfigSnapshotEntity.class));
    }

    @Test
    void damagedFirstSubmissionSnapshotsFailWithoutConsultingTheModelSupplier() {
        ScriptAnalysisTaskEntity task = new ScriptAnalysisTaskEntity();
        task.setId(41L);
        ScriptAnalysisConfigSnapshotEntity stored = new ScriptAnalysisConfigSnapshotEntity();
        when(mapper.selectOne(any())).thenReturn(stored);
        @SuppressWarnings("unchecked")
        Supplier<Long> initialModel = mock(Supplier.class);
        for (String payload : java.util.List.of("{}", "{\"modelId\":0}", "{\"modelId\":1.5}", "invalid")) {
            stored.setSnapshotJson(payload);
            assertThatThrownBy(() -> service.modelIdForFirstSubmission(task, initialModel))
                .isInstanceOf(IllegalStateException.class);
        }
        verifyNoInteractions(initialModel);
        verify(mapper, never()).insert(any(ScriptAnalysisConfigSnapshotEntity.class));
    }

    @Test
    void retriesKeepTheOriginallySelectedModelWithoutQueryingLegacyDefinitions() throws Exception {
        ScriptAnalysisTaskEntity task = new ScriptAnalysisTaskEntity();
        task.setId(41L);
        ScriptAnalysisConfigSnapshotEntity stored = new ScriptAnalysisConfigSnapshotEntity();
        stored.setSnapshotJson("{\"modelId\":12}");
        when(mapper.selectOne(any())).thenReturn(null, stored, stored);
        service.snapshot(task, 12L);
        service.snapshot(task, 99L);
        assertThat(service.modelIdFor(41L)).isEqualTo(12L);
        var captured = ArgumentCaptor.forClass(ScriptAnalysisConfigSnapshotEntity.class);
        verify(mapper).insert(captured.capture());
        assertThat(new ObjectMapper().readTree(captured.getValue().getSnapshotJson()).size()).isEqualTo(1);
        assertThat(captured.getValue().getTaskId()).isEqualTo(41L);
    }

    @Test
    void missingSnapshotsFailInsteadOfSelectingTheCurrentProjectModel() {
        assertThatThrownBy(() -> service.modelIdFor(41L))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("快照缺失");
    }

    @Test
    void damagedSnapshotsFailInsteadOfSilentlyChangingModels() {
        ScriptAnalysisConfigSnapshotEntity stored = new ScriptAnalysisConfigSnapshotEntity();
        when(mapper.selectOne(any())).thenReturn(stored);
        for (String payload : java.util.List.of("{}", "{\"modelId\":0}", "invalid")) {
            stored.setSnapshotJson(payload);
            assertThatThrownBy(() -> service.modelIdFor(41L)).isInstanceOf(IllegalStateException.class);
        }
    }
}
