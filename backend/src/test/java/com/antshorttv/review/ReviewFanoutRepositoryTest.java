package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ReviewFanoutRepositoryTest {
    @Autowired private ReviewFanoutRepository repository;
    @Autowired private JdbcTemplate jdbc;
    private long modelId;

    @BeforeEach
    void seedReviewTask() {
        modelId = jdbc.queryForObject("select id from ai_model order by id limit 1", Long.class);
        jdbc.update("insert into review_project (id, tenant_id, name, source_type, original_content, status, created_by, created_at, updated_at) values (98501, 98500, 'R', 'TXT', 'abc', 'ACTIVE', 1, now(), now())");
        jdbc.update("insert into review_script_version (id, tenant_id, project_id, version_no, source_type, content, created_by, created_at, updated_at) values (98502, 98500, 98501, 1, 'TXT', 'abc', 1, now(), now())");
        jdbc.update("insert into review_task (id, tenant_id, project_id, script_version_id, round_no, review_mode, selected_dimensions_json, review_scope_type, status, overall_progress, idempotency_key, created_by, created_at, updated_at) values (98503, 98500, 98501, 98502, 1, 'DEEP', '[\"台词合理性\"]', 'ALL', 'PENDING', 0, 'repo-98503', 1, now(), now())");
    }

    @Test
    void keepsAttemptIdempotentUnitsUniqueAndOrdered() {
        long first = repository.openSnapshot(snapshot(1));
        long second = repository.openSnapshot(snapshot(1));
        assertThat(second).isEqualTo(first);

        repository.addUnit(new ReviewFanoutRepository.UnitDraft(first, 2, "u2", "{}", 3, 6, "f2"));
        long unit1 = repository.addUnit(new ReviewFanoutRepository.UnitDraft(first, 1, "u1", "{}", 0, 3, "f1"));
        assertThat(repository.orderedUnits(first)).extracting(ReviewFanoutUnitEntity::getId)
            .containsExactly(unit1, unit1 - 1);
        assertThatThrownBy(() -> repository.addUnit(
            new ReviewFanoutRepository.UnitDraft(first, 3, "u1", "{}", 6, 9, "f3")))
            .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void replacesCurrentCandidateFindsHashesAndUsesCompareAndSetStatus() {
        long snapshotId = repository.openSnapshot(snapshot(1));
        long unitId = repository.addUnit(new ReviewFanoutRepository.UnitDraft(snapshotId, 1, "u1", "{}", 0, 3, "f1"));
        long run1 = insertRun();
        long run2 = insertRun();

        repository.replaceCandidate(candidate(snapshotId, unitId, run1, "payload-1"));
        repository.replaceCandidate(candidate(snapshotId, unitId, run2, "payload-2"));

        assertThat(repository.currentCandidate(snapshotId, unitId).getPayloadHash()).isEqualTo("payload-2");
        assertThat(jdbc.queryForObject("select count(*) from review_unit_result where unit_id = ?", Integer.class, unitId)).isOne();
        assertThat(repository.findMatchingSnapshot(98503, "version", "scope", "dimensions", "units"))
            .isEqualTo(snapshotId);
        assertThat(repository.findMatchingSnapshot(98503, "version", "scope", "dimensions", "changed-units"))
            .isNull();
        assertThat(repository.findMatchingSnapshot(98503, "version", "changed-scope", "dimensions", "units"))
            .isNull();
        assertThat(repository.findMatchingSnapshot(98503, "version", "scope", "changed-dimensions", "units"))
            .isNull();
        assertThat(repository.transitionUnit(unitId, ReviewUnitStatus.PENDING, ReviewUnitStatus.RUNNING)).isTrue();
        assertThat(repository.transitionUnit(unitId, ReviewUnitStatus.PENDING, ReviewUnitStatus.RUNNING)).isFalse();
    }

    private ReviewFanoutRepository.SnapshotDraft snapshot(int attempt) {
        return new ReviewFanoutRepository.SnapshotDraft(98500, 98501, 98503, 98502, attempt,
            "script-review", 1, "[]", modelId, "[\"台词合理性\"]", "{}",
            "version", "scope", "dimensions", "units", 2, 2);
    }

    private ReviewFanoutRepository.CandidateDraft candidate(long snapshot, long unit, long run, String hash) {
        return new ReviewFanoutRepository.CandidateDraft(snapshot, unit, run, 1, "version", "scope",
            "dimensions", "f1", "{}", "[]", hash);
    }

    private long insertRun() {
        jdbc.update("insert into ai_workflow_agent_run (agent_code, run_type, tenant_id, user_id, project_id, task_id, status, model_id, temperature, max_tokens, max_steps, prompt_snapshot, started_at, created_at) values ('script-review', 'REVIEW_CHILD', 98500, 1, 98501, 98503, 'RUNNING', ?, 0.1, 4096, 20, '', now(), now())", modelId);
        return jdbc.queryForObject("select max(id) from ai_workflow_agent_run", Long.class);
    }
}
