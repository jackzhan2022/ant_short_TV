package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.common.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class AssetRecognitionFinalizerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AssetRecognitionFinalizer finalizer = new AssetRecognitionFinalizer(jdbc);

    @Test
    void partialCoverageDoesNotRetireAnything() {
        when(jdbc.queryForList(anyString(), any(Object[].class)))
            .thenReturn(List.of(Map.of("tenant_id", 1L, "project_id", 2L, "script_id", 3L)));
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenReturn(1);

        assertThatThrownBy(() -> finalizer.finish(9L)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("不能退役");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void completeCoverageRetiresOnlyAgentManagedUnboundRows() {
        when(jdbc.queryForList(anyString(), any(Object[].class)))
            .thenReturn(List.of(Map.of("tenant_id", 1L, "project_id", 2L, "script_id", 3L)));
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenReturn(0);

        finalizer.finish(9L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeast(7)).update(sql.capture(), any(Object[].class));
        String statements = String.join("\n", sql.getAllValues());
        org.assertj.core.api.Assertions.assertThat(statements)
            .contains("generated_by_run_id is not null", "source = 'AI'", "not exists")
            .doesNotContain("source = 'USER'");
    }
}
