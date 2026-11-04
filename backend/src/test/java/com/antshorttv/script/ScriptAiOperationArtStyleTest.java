package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.antshorttv.ai.*;
import com.antshorttv.execution.*;
import com.antshorttv.security.TenantContext;
import com.antshorttv.style.AssetArtStyleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ScriptAiOperationArtStyleTest {
    private final ScriptAiOperationMapper operations = mock(ScriptAiOperationMapper.class);
    private final AiExecutionService executions = mock(AiExecutionService.class);
    private final ProjectAiConfigService models = mock(ProjectAiConfigService.class);
    private final AssetArtStyleService styles = mock(AssetArtStyleService.class);
    private final AssetExtractionCoordination coordination = mock(AssetExtractionCoordination.class);
    private final ScriptAiOperationService service = new ScriptAiOperationService(operations, executions,
        mock(AiExecutionResponseMapper.class), models, new ObjectMapper(), mock(StoryboardGenerationAdmissionRepository.class));

    private void setup() {
        ReflectionTestUtils.setField(service, "artStyles", styles);
        ReflectionTestUtils.setField(service, "assetCoordination", coordination);
        when(models.resolveModelId(7L, 8L, "TEXT")).thenReturn(99L);
        when(coordination.fingerprint(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyString()))
            .thenAnswer(call -> call.getArgument(6));
        when(styles.rules(anyString(), eq("ALL"))).thenAnswer(call -> call.getArgument(0));
    }

    private void submit(String key) {
        service.submit(new TenantContext(1L, 7L, 2L, "OWNER"), 8L, AiBusinessScene.SCOPED_ASSET_REEXTRACTION,
            "SCOPED_ASSET_REEXTRACTION", 9L, 10L,
            new ScopedAssetReextractionRequest("ALL", "REGENERATE_ALL"), key, "test-art-style");
    }

    @Test void aChangedStyleCannotReuseTheOldActiveReextraction() {
        setup();
        when(styles.snapshot(7L, 8L)).thenReturn("STYLE_A", "STYLE_B");
        AtomicReference<String> active = new AtomicReference<>();
        when(coordination.admit(eq(7L), eq(8L), eq(9L), anyString())).thenAnswer(call -> {
            String fingerprint = call.getArgument(3);
            if (active.get() != null && !active.get().equals(fingerprint)) throw new AssetExtractionConflictException(88L);
            active.set(fingerprint);
            return 88L;
        });
        submit("first");
        assertThatThrownBy(() -> submit("second")).isInstanceOf(AssetExtractionConflictException.class);
        verify(operations, never()).insert(any(ScriptAiOperationEntity.class));
    }

    @Test void persistsTheExactSnapshotUsedForAdmission() {
        setup();
        when(styles.snapshot(7L, 8L)).thenReturn("FROZEN_STYLE");
        when(coordination.admit(anyLong(), anyLong(), anyLong(), anyString())).thenReturn(null);
        var execution = new AiExecutionTaskEntity(); execution.id = 88L;
        when(executions.createWithReservation(any(), any(), any())).thenReturn(execution);
        submit("first");
        var operation = ArgumentCaptor.forClass(ScriptAiOperationEntity.class);
        verify(operations).insert(operation.capture());
        assertThat(operation.getValue().artStyleSnapshot).isEqualTo("FROZEN_STYLE");
        verify(styles, times(1)).snapshot(7L, 8L);
        verify(coordination).fingerprint(eq(7L), eq(8L), eq(9L), eq(1L), eq(10L), eq(99L), contains("FROZEN_STYLE"));
    }
}
