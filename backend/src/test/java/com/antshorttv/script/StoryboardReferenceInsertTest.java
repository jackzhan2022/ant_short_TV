package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class StoryboardReferenceInsertTest {
    @Test void insertsFreshReferencesWithoutRetiringAnEmptyIndexRange() {
        var jdbc=mock(JdbcTemplate.class);var mapper=mock(StoryboardAssetReferenceMapper.class);
        var repository=new StoryboardAssetReferenceRepository(jdbc,mapper);
        var reference=new StoryboardAssetReferenceEntity();
        reference.id=999L;reference.tenantId=99L;reference.projectId=99L;reference.storyboardId=99L;
        reference.assetType="CHARACTER";reference.assetId=11L;reference.variantId=12L;
        reference.referenceRole="VISIBLE";reference.sortOrder=0;reference.lockedByUser=true;
        var now=LocalDateTime.now();
        repository.insertNew(1L,2L,3L,List.of(reference),now);
        verifyNoInteractions(jdbc);
        verify(mapper).insert(reference);
        assertThat(reference.id).isNull();
        assertThat(reference.tenantId).isEqualTo(1L);
        assertThat(reference.projectId).isEqualTo(2L);
        assertThat(reference.storyboardId).isEqualTo(3L);
        assertThat(reference.variantId).isEqualTo(12L);
        assertThat(reference.lockedByUser).isTrue();
        assertThat(reference.createdAt).isEqualTo(now);
        assertThat(reference.retiredAt).isNull();
    }
    @Test void emptyFreshReferenceListsDoNotIssueUpdatesOrInserts() {
        var jdbc=mock(JdbcTemplate.class);var mapper=mock(StoryboardAssetReferenceMapper.class);
        var repository=new StoryboardAssetReferenceRepository(jdbc,mapper);
        repository.insertNew(1L,2L,3L,List.of(),LocalDateTime.now());
        repository.insertNew(1L,2L,3L,null,LocalDateTime.now());
        verifyNoInteractions(jdbc,mapper);
    }
}
