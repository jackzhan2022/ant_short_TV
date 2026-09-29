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
class StoryboardAssetReferenceLegacyResolverTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StoryboardAssetReferenceRepository repository;
    @Autowired private StoryboardAssetReferenceLegacyResolver resolver;
    @Autowired private StoryboardAssetReferenceBackfillService backfill;

    @BeforeEach
    void seedLegacyData() {
        jdbc.update("insert into tenant (id,code,name,type,status,created_at,updated_at) values (9961,'legacy-ref','Legacy','TEAM','ACTIVE',now(),now())");
        jdbc.update("insert into project (id,tenant_id,name,code,owner_id,status,created_by,created_at,updated_at) values (9962,9961,'Legacy','legacy-ref',1,'ACTIVE',1,now(),now())");
        jdbc.update("insert into storyboard (id,tenant_id,project_id,episode_no,shot_no,visual_description,characters,scene,props,status,created_by,created_at,updated_at) values (9963,9961,9962,1,1,'legacy','Serena、Sera','走廊','遗失道具','DRAFT',1,now(),now())");
        jdbc.update("insert into storyboard (id,tenant_id,project_id,episode_no,shot_no,visual_description,characters,status,created_by,created_at,updated_at) values (9964,9961,9962,1,2,'formal','Serena','DRAFT',1,now(),now())");
        jdbc.update("insert into character_asset (id,tenant_id,project_id,name,normalized_name,content_json,role_type,status,created_by,created_at,updated_at) values (9971,9961,9962,'Serena','serena','{\"aliases\":[\"Sera\"]}','LEAD','CONFIRMED',1,now(),now())");
        jdbc.update("insert into character_asset (id,tenant_id,project_id,name,normalized_name,content_json,role_type,status,created_by,created_at,updated_at) values (9972,9961,9962,'Serena Aldwych','serenaaldwych','{\"aliases\":[\"Sera\"]}','LEAD','CONFIRMED',1,now(),now())");
        jdbc.update("insert into scene_asset (id,tenant_id,project_id,name,normalized_name,content_json,scene_type,status,created_by,created_at,updated_at) values (9973,9961,9962,'走廊','走廊','{\"aliases\":[\"长廊\"]}','INTERIOR','CONFIRMED',1,now(),now())");

        StoryboardAssetReferenceEntity formal = entity("CHARACTER", 9971L, "Serena", 0);
        repository.replace(9961L, 9962L, 9964L, List.of(formal), LocalDateTime.now());
    }

    @Test
    void resolvesExactAndUniqueNamesButPreservesAmbiguousAndMissingValues() {
        var result = resolver.resolveStoryboard(9961L, 9962L, 9963L);

        assertThat(result.formal()).isFalse();
        assertThat(result.references()).extracting(item ->
            item.assetType() + ":" + item.sourceName() + ":" + item.resolutionStatus())
            .containsExactly(
                "CHARACTER:Serena:ASSET_PENDING",
                "CHARACTER:Sera:UNRESOLVED",
                "SCENE:走廊:ASSET_PENDING",
                "PROP:遗失道具:UNRESOLVED"
            );
        assertThat(result.references().get(0).assetId()).isEqualTo(9971L);
        assertThat(result.references().get(1).assetId()).isNull();

        assertThat(resolver.resolveStoryboard(9961L, 9962L, 9964L).formal()).isTrue();
    }

    @Test
    void backfillIsBoundedIdempotentAndKeepsLegacyFields() {
        StoryboardAssetReferenceBackfillResult first = backfill.backfill(10);
        StoryboardAssetReferenceBackfillResult second = backfill.backfill(10);

        assertThat(first.processed()).isEqualTo(1);
        assertThat(first.resolved()).isEqualTo(2);
        assertThat(first.unresolved()).isEqualTo(2);
        assertThat(first.failed()).isZero();
        assertThat(second.processed()).isZero();
        assertThat(repository.listActive(9961L, 9962L, 9963L)).hasSize(4);
        assertThat(jdbc.queryForMap("select characters,scene,props from storyboard where id=9963"))
            .containsEntry("CHARACTERS", "Serena、Sera")
            .containsEntry("SCENE", "走廊")
            .containsEntry("PROPS", "遗失道具");
        assertThat(backfill.countConsistencyDrift()).isZero();
    }

    private StoryboardAssetReferenceEntity entity(String type, Long assetId, String name, int order) {
        StoryboardAssetReferenceEntity entity = new StoryboardAssetReferenceEntity();
        entity.assetType = type;
        entity.assetId = assetId;
        entity.referenceRole = "VISIBLE";
        entity.sortOrder = order;
        entity.resolutionStatus = "ASSET_PENDING";
        entity.sourceType = "MANUAL";
        entity.sourceName = name;
        entity.lockedByUser = true;
        entity.createdBy = 1L;
        return entity;
    }
}
