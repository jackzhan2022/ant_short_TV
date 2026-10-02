package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.antshorttv.common.BusinessException;
import java.sql.ResultSet;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AssetVisualPagingQueryBoundaryTest {
    @Test
    @SuppressWarnings("unchecked")
    void readsOneProjectedPageAndJoinsMediaInsteadOfExpandingEveryVariant() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AssetVisualVariantMapper variants = mock(AssetVisualVariantMapper.class);
        AssetVisualVariantService service = new AssetVisualVariantService(variants, jdbc);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L), eq(2L), eq(3L))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(1L), eq(2L), eq("CHARACTER"), eq(3L))).thenReturn(27L);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(1L), eq(2L), eq("CHARACTER"), eq(3L), eq(20), eq(0L)))
            .thenAnswer(call -> {
                RowMapper<?> mapper = call.getArgument(1);
                var rows = new ArrayList<>();
                for (int index = 0; index < 20; index++) {
                    ResultSet row = mock(ResultSet.class);
                    when(row.getLong("id")).thenReturn((long) index + 1);
                    when(row.getLong("project_id")).thenReturn(2L);
                    when(row.getLong("asset_id")).thenReturn(3L);
                    when(row.getString("asset_type")).thenReturn("CHARACTER");
                    when(row.getString("generation_status")).thenReturn("COMPLETED");
                    when(row.getString("display_path")).thenReturn("materials/1/2/image/derived/display.png");
                    when(row.getObject("current_image_result_id", Long.class)).thenReturn(7L);
                    rows.add(mapper.mapRow(row, index));
                }
                return rows;
            });
        var page = service.page(1L, 2L, "CHARACTER", 3L, null, null);
        assertThat(page.data()).hasSize(20);
        assertThat(page.total()).isEqualTo(27);
        assertThat(page.data().get(0).currentImageThumbnailUrl()).isEqualTo("/api/projects/2/ai-image-results/7/thumbnail");
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(1)).query(query.capture(), any(RowMapper.class), eq(1L), eq(2L), eq("CHARACTER"), eq(3L), eq(20), eq(0L));
        assertThat(query.getValue()).contains("left join ai_image_result", "image.status = 'ACTIVE'",
            "order by v.is_primary desc, v.id asc limit ? offset ?", "v.tenant_id = ?", "v.asset_id = ?")
            .doesNotContain("content_json", "v.*");
        verifyNoInteractions(variants);
    }

    @Test
    void invalidAssetScopeIsRejectedBeforeCandidateOrMediaReads() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AssetVisualVariantService service = new AssetVisualVariantService(mock(AssetVisualVariantMapper.class), jdbc);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L), eq(2L), eq(999L))).thenReturn(0);
        assertThatThrownBy(() -> service.page(1L, 2L, "CHARACTER", 999L, 1, 20))
            .isInstanceOf(BusinessException.class);
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class), any(Object[].class));
        verify(jdbc, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void readsOnlyActiveBindingsForPageAndPinnedVariantIds() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AssetVisualVariantService service = new AssetVisualVariantService(mock(AssetVisualVariantMapper.class), jdbc);
        service.relevantEpisodeBindings(1L, 2L, "CHARACTER", 3L, java.util.List.of(99L, 100L));
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(query.capture(), any(RowMapper.class), eq(1L), eq(2L), eq("CHARACTER"), eq(3L), eq(99L), eq(100L));
        assertThat(query.getValue()).contains("b.binding_status = 'ACTIVE'", "e.status = 'ACTIVE'",
            "e.retired_at is null", "b.retired_at is null", "b.variant_id in (", "?,?")
            .doesNotContain("content_json");
    }
}
