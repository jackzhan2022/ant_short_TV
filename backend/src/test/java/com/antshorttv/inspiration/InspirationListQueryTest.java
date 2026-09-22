package com.antshorttv.inspiration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antshorttv.security.CurrentPrincipal;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InspirationListQueryTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
            InspirationCreationEntity.class
        );
    }

    @Test
    void publicListDoesNotOrderCountQuery() {
        InspirationCreationMapper mapper = mock(InspirationCreationMapper.class);
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.selectList(any())).thenReturn(List.of());
        InspirationCreationService service = new InspirationCreationService(
            mapper,
            mock(InspirationCreationMediaStorage.class),
            new ObjectMapper(),
            mock(CurrentPrincipal.class)
        );

        service.list(1, 8);

        assertCountIsUnorderedAndListIsOrdered(mapper);
    }

    @Test
    void managementListDoesNotOrderCountQuery() {
        InspirationCreationMapper mapper = mock(InspirationCreationMapper.class);
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.selectList(any())).thenReturn(List.of());
        InspirationManagementService service = new InspirationManagementService(
            mapper,
            mock(InspirationManagementMediaService.class),
            mock(InspirationCreationMediaStorage.class),
            new ObjectMapper()
        );

        service.list(1, 20, null, null, null);

        assertCountIsUnorderedAndListIsOrdered(mapper);
    }

    private void assertCountIsUnorderedAndListIsOrdered(InspirationCreationMapper mapper) {
        ArgumentCaptor<Wrapper<InspirationCreationEntity>> countQuery = wrapperCaptor();
        ArgumentCaptor<Wrapper<InspirationCreationEntity>> listQuery = wrapperCaptor();
        org.mockito.Mockito.verify(mapper).selectCount(countQuery.capture());
        org.mockito.Mockito.verify(mapper).selectList(listQuery.capture());

        assertThat(countQuery.getValue().getSqlSegment()).doesNotContainIgnoringCase("ORDER BY");
        assertThat(listQuery.getValue().getSqlSegment()).containsIgnoringCase("ORDER BY");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<Wrapper<InspirationCreationEntity>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
    }
}
