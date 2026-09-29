package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StoryboardAssetReferenceRepositoryTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StoryboardAssetReferenceRepository repository;

    @BeforeEach
    void seedStoryboards() {
        jdbc.update("insert into tenant (id,code,name,type,status,created_at,updated_at) values (9801,'ref-a','A','TEAM','ACTIVE',now(),now())");
        jdbc.update("insert into tenant (id,code,name,type,status,created_at,updated_at) values (9802,'ref-b','B','TEAM','ACTIVE',now(),now())");
        jdbc.update("insert into project (id,tenant_id,name,code,owner_id,status,created_by,created_at,updated_at) values (9811,9801,'A','ref-a',1,'ACTIVE',1,now(),now())");
        jdbc.update("insert into project (id,tenant_id,name,code,owner_id,status,created_by,created_at,updated_at) values (9812,9802,'B','ref-b',1,'ACTIVE',1,now(),now())");
        jdbc.update("insert into storyboard (id,tenant_id,project_id,episode_no,shot_no,visual_description,status,created_by,created_at,updated_at) values (9821,9801,9811,1,1,'one','DRAFT',1,now(),now())");
        jdbc.update("insert into storyboard (id,tenant_id,project_id,episode_no,shot_no,visual_description,status,created_by,created_at,updated_at) values (9822,9801,9811,1,2,'two','DRAFT',1,now(),now())");
        jdbc.update("insert into storyboard (id,tenant_id,project_id,episode_no,shot_no,visual_description,status,created_by,created_at,updated_at) values (9823,9802,9812,1,1,'other','DRAFT',1,now(),now())");
    }

    @Test
    void replacesAndReadsActiveReferencesInDeterministicOrder() {
        repository.replace(9801L, 9811L, 9821L, List.of(
            reference("PROP", 33L, 0),
            reference("CHARACTER", 11L, 1),
            reference("CHARACTER", 12L, 0),
            reference("SCENE", 22L, 0)
        ), LocalDateTime.now());

        assertThat(repository.listActive(9801L, 9811L, 9821L))
            .extracting(item -> item.assetType + ":" + item.assetId)
            .containsExactly("CHARACTER:12", "CHARACTER:11", "SCENE:22", "PROP:33");
        assertThat(repository.listActive(9802L, 9812L, 9821L)).isEmpty();

        repository.replace(9801L, 9811L, 9821L,
            List.of(reference("SCENE", 24L, 0)), LocalDateTime.now());

        assertThat(repository.listActive(9801L, 9811L, 9821L))
            .extracting(item -> item.assetType + ":" + item.assetId)
            .containsExactly("SCENE:24");
        assertThat(jdbc.queryForObject("""
            select count(*) from storyboard_asset_reference
             where storyboard_id = 9821 and retired_at is not null
            """, Integer.class)).isEqualTo(4);
    }

    @Test
    void loadsManyStoryboardsWithOneBoundedRepositoryCall() {
        repository.replace(9801L, 9811L, 9821L,
            List.of(reference("CHARACTER", 11L, 0)), LocalDateTime.now());
        repository.replace(9801L, 9811L, 9822L,
            List.of(reference("SCENE", 22L, 0), reference("PROP", 33L, 0)), LocalDateTime.now());

        var grouped = repository.listActiveForStoryboards(9801L, 9811L, List.of(9821L, 9822L));

        assertThat(grouped).containsOnlyKeys(9821L, 9822L);
        assertThat(grouped.get(9821L)).hasSize(1);
        assertThat(grouped.get(9822L)).hasSize(2);
    }

    private StoryboardAssetReferenceEntity reference(String type, Long assetId, int order) {
        StoryboardAssetReferenceEntity entity = new StoryboardAssetReferenceEntity();
        entity.assetType = type;
        entity.assetId = assetId;
        entity.referenceRole = "CHARACTER".equals(type) ? "VISIBLE" : "MAIN";
        entity.sortOrder = order;
        entity.resolutionStatus = "ASSET_PENDING";
        entity.sourceType = "MANUAL";
        entity.sourceName = type + assetId;
        entity.lockedByUser = true;
        entity.createdBy = 1L;
        return entity;
    }
}
