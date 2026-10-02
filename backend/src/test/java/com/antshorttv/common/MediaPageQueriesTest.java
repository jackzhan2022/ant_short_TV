package com.antshorttv.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class MediaPageQueriesTest {
    @Test
    @SuppressWarnings("unchecked")
    void skipsSelectForOutOfRangePageAndKeepsDatabaseTotal() {
        BaseMapper<Object> mapper = mock(BaseMapper.class);
        when(mapper.selectCount(any())).thenReturn(25L);
        var page = MediaPageQueries.select(mapper, new QueryWrapper<>(), PageBounds.of(3, 20));
        assertThat(page.total()).isEqualTo(25);
        assertThat(page.data()).isEmpty();
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).selectList(any());
    }

    @Test
    void representativesUseOnlyBoundedTaskIdsAndIncludeSelectedPlusLatest() {
        var query = MediaPageQueries.representativeQuery("ai_image_result", 1L, 2L,
            List.of(3L, 4L), "ACTIVE", "is_selected");
        assertThat(query.getSqlSegment()).contains("tenant_id", "project_id", "task_id", "max(id)", "is_selected");
        assertThat(query.getSqlSegment()).contains("3,4");
    }
}
