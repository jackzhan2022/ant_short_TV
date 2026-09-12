package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.antshorttv.project.ProjectAccessResolver;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AssetSummaryQueryBoundaryTest {
    @Test
    void mapsManyAssetsWithThreeQueriesAndNoVisualExpansion() throws Exception {
        Map<Class<?>, Object> dependencies = new HashMap<>();
        var constructor = ScriptWorkflowService.class.getConstructors()[0];
        Object[] arguments = Arrays.stream(constructor.getParameterTypes())
            .map(type -> dependencies.computeIfAbsent(type, key -> mock(key, RETURNS_DEEP_STUBS)))
            .toArray();
        ScriptWorkflowService service = (ScriptWorkflowService) constructor.newInstance(arguments);
        var tenant = (TenantContextResolver) dependencies.get(TenantContextResolver.class);
        when(tenant.requireActiveMember(10L)).thenReturn(new TenantContext(1L, 10L, 1L, "OWNER"));
        var scripts = (ScriptMapper) dependencies.get(ScriptMapper.class);
        when(scripts.selectCurrentByProject(10L, 33L)).thenReturn(null);
        var jdbc = (JdbcTemplate) dependencies.get(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(10L), eq(33L), isNull()))
            .thenAnswer(invocation -> {
                RowMapper<?> mapper = invocation.getArgument(1);
                ResultSet row = mock(ResultSet.class);
                when(row.getLong("id")).thenReturn(1L);
                when(row.getString("name")).thenReturn("Asset");
                when(row.getObject("main_image_result_id", Long.class)).thenReturn(7L);
                var results = new java.util.ArrayList<>();
                for (int index = 0; index < 50; index++) results.add(mapper.mapRow(row, index));
                return results;
            });
        var result = service.assetSettingsSummary(10L, 33L);
        assertThat(result.characters()).hasSize(50);
        assertThat(result.scenes()).hasSize(50);
        assertThat(result.props()).hasSize(50);
        assertThat(result.characters().get(0).mainImageThumbnailUrl())
            .isEqualTo("/api/projects/33/ai-image-results/7/thumbnail");
        verify(jdbc, times(3)).query(anyString(), any(RowMapper.class), eq(10L), eq(33L), isNull());
        ArgumentCaptor<String> queries = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(3)).query(queries.capture(), any(RowMapper.class), eq(10L), eq(33L), isNull());
        assertThat(queries.getAllValues()).allSatisfy(query ->
            assertThat(query).contains("main_image_result_id"));
        verifyNoMoreInteractions(jdbc);
        verify((ProjectAccessResolver) dependencies.get(ProjectAccessResolver.class)).requireView(10L, 33L);
        for (Class<?> type : List.of(AssetVisualVariantService.class, AssetVisualBindingService.class,
            EpisodeAwareVisualResolver.class, ScriptAssetCandidateReviewService.class)) {
            verifyNoInteractions(dependencies.get(type));
        }
    }
}
