package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ScriptAssetCandidateReviewServiceTest {
    @Autowired private ScriptAssetNormalizationService normalizationService;
    @Autowired private ScriptAssetCandidateReviewService reviewService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void pagesCandidateReviewQueueWithinTenantAndProject() {
        prepareScript(9301L, 9302L, 9303L, 9304L);
        normalizationService.normalizeAndPersist(9301L, 9302L, 9303L, 9304L,
            null, null, null, null, null, "run-9301",
            "{\"characters\":[\"林夏\",\"顾言\",\"周姨\"]}");

        var first = reviewService.listPage(9301L, 9302L, "PENDING_REVIEW", "CHARACTER", 1, 2);
        var second = reviewService.listPage(9301L, 9302L, "PENDING_REVIEW", "CHARACTER", 2, 2);

        assertThat(first.total()).isEqualTo(3);
        assertThat(first.items()).hasSize(2);
        assertThat(second.items()).hasSize(1);
        assertThat(first.page()).isEqualTo(1);
        assertThat(first.pageSize()).isEqualTo(2);
    }

    @Test
    void acceptsNewCandidateOnceWhenDecisionIsRetried() {
        long candidateId = candidate(9401L, 9402L, 9403L, 9404L,
            "{\"characters\":[{\"name\":\"林夏\",\"roleType\":\"LEAD\"}]}");
        var command = new ScriptAssetCandidateReviewService.DecisionCommand(
            "ACCEPT_NEW", null, "accept-new-9401");

        var first = reviewService.decide(9401L, 9402L, candidateId, 9499L, command);
        var retried = reviewService.decide(9401L, 9402L, candidateId, 9499L, command);

        assertThat(first.resultAssetId()).isEqualTo(retried.resultAssetId());
        assertThat(jdbc.queryForObject(
            "select count(*) from character_asset where tenant_id = 9401 and project_id = 9402 and name = '林夏'",
            Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select count(*) from script_asset_promotion_decision where tenant_id = 9401",
            Integer.class)).isEqualTo(1);
    }

    @Test
    void mergesIntoConfirmedOwnedTargetAndSupportsExplicitRetargeting() {
        prepareScript(9501L, 9502L, 9503L, 9504L);
        jdbc.update("""
            insert into character_asset
              (id, tenant_id, project_id, name, role_type, status, merge_target_id, created_by, created_at, updated_at)
            values (9510, 9501, 9502, '林夏', 'SUPPORTING', 'CONFIRMED', null, 9599, now(), now())
            """);
        normalizationService.normalizeAndPersist(9501L, 9502L, 9503L, 9504L,
            null, null, null, null, null, "run-9501",
            "{\"characters\":[{\"name\":\"林夏\",\"roleType\":\"LEAD\",\"appearance\":\"黑色风衣\"}]}");
        long candidateId = latestCandidate(9501L);

        reviewService.decide(9501L, 9502L, candidateId, 9599L,
            new ScriptAssetCandidateReviewService.DecisionCommand("RETARGET", 9510L, "retarget-9501"));
        var merged = reviewService.decide(9501L, 9502L, candidateId, 9599L,
            new ScriptAssetCandidateReviewService.DecisionCommand("ACCEPT_MERGE", 9510L, "merge-9501"));

        assertThat(merged.resultAssetId()).isEqualTo(9510L);
        assertThat(jdbc.queryForMap("select role_type, appearance from character_asset where id = 9510"))
            .containsEntry("role_type", "LEAD")
            .containsEntry("appearance", "黑色风衣");
    }

    @Test
    void rejectsCandidateWithoutChangingCanonicalAssets() {
        long candidateId = candidate(9601L, 9602L, 9603L, 9604L,
            "{\"props\":[{\"name\":\"录音笔\"}]}");

        reviewService.decide(9601L, 9602L, candidateId, 9699L,
            new ScriptAssetCandidateReviewService.DecisionCommand("REJECT", null, "reject-9601"));

        assertThat(jdbc.queryForObject(
            "select review_status from script_asset_candidate where id = ?", String.class, candidateId))
            .isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject(
            "select count(*) from prop_asset where tenant_id = 9601", Integer.class)).isZero();
    }

    @Test
    void rejectsStaleRunsAndCrossTenantMergeTargets() {
        long staleCandidate = candidate(9701L, 9702L, 9703L, 9704L,
            "{\"scenes\":[{\"name\":\"天台\"}]}");
        jdbc.update("update script set current_version_id = 9790 where id = 9703");

        assertThatThrownBy(() -> reviewService.decide(9701L, 9702L, staleCandidate, 9799L,
            new ScriptAssetCandidateReviewService.DecisionCommand("ACCEPT_NEW", null, "stale-9701")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("旧剧本版本");

        long candidate = candidate(9801L, 9802L, 9803L, 9804L,
            "{\"characters\":[{\"name\":\"林夏\"}]}");
        jdbc.update("""
            insert into character_asset
              (id, tenant_id, project_id, name, role_type, status, merge_target_id, created_by, created_at, updated_at)
            values (9810, 9991, 9802, '林夏', 'LEAD', 'CONFIRMED', null, 9899, now(), now())
            """);
        assertThatThrownBy(() -> reviewService.decide(9801L, 9802L, candidate, 9899L,
            new ScriptAssetCandidateReviewService.DecisionCommand("ACCEPT_MERGE", 9810L, "cross-9801")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不属于当前项目");
    }

    private long candidate(long tenantId, long projectId, long scriptId, long versionId, String raw) {
        prepareScript(tenantId, projectId, scriptId, versionId);
        normalizationService.normalizeAndPersist(tenantId, projectId, scriptId, versionId,
            null, null, null, null, null, "run-" + tenantId, raw);
        return latestCandidate(tenantId);
    }

    private long latestCandidate(long tenantId) {
        return jdbc.queryForObject(
            "select id from script_asset_candidate where tenant_id = ? order by id desc limit 1",
            Long.class, tenantId);
    }

    private void prepareScript(long tenantId, long projectId, long scriptId, long versionId) {
        jdbc.update("""
            insert into script
              (id, tenant_id, project_id, title, source_type, content, status, current_version_id,
               created_by, created_at, updated_at)
            values (?, ?, ?, '测试剧本', 'MANUAL_EDIT', '正文', 'DRAFT', ?, 9999, now(), now())
            """, scriptId, tenantId, projectId, versionId);
        jdbc.update("""
            insert into script_version
              (id, tenant_id, project_id, script_id, version_no, source_type, content, status, created_by, created_at)
            values (?, ?, ?, ?, 1, 'MANUAL_EDIT', '正文', 'CURRENT', 9999, now())
            """, versionId, tenantId, projectId, scriptId);
    }
}
