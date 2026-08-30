package com.antshorttv.workflowagent.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSkillRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void createsListsUpdatesCopiesAndDeletesCompleteSkillFiles() throws Exception {
        FileSkillRepository repository = repository();
        SkillDocument created = repository.create("script-rewriter", skill("script-rewriter", "first"));

        assertThat(Files.readString(tempDir.resolve("script-rewriter/SKILL.md"))).isEqualTo(created.content());
        assertThat(repository.list()).extracting(SkillDocument::code).containsExactly("script-rewriter");

        SkillDocument updated = repository.update(
            "script-rewriter", skill("script-rewriter", "second"), created.revision());
        SkillDocument copied = repository.copy("script-rewriter", "script-rewriter-copy");

        assertThat(updated.content()).contains("second");
        assertThat(copied.code()).isEqualTo("script-rewriter-copy");
        assertThat(copied.content()).isEqualTo(updated.content());

        repository.delete("script-rewriter-copy");
        assertThat(repository.exists("script-rewriter-copy")).isFalse();
        assertThat(Files.exists(tempDir.resolve("script-rewriter-copy"))).isFalse();
    }

    @Test
    void rejectsDuplicateCreateStaleUpdateAndOversizedContentWithoutDamagingCurrentFile() {
        FileSkillRepository repository = repository();
        SkillDocument created = repository.create("safe-skill", skill("safe-skill", "current"));

        assertThatThrownBy(() -> repository.create("safe-skill", skill("safe-skill", "duplicate")))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorCode())
            .isEqualTo(ErrorCode.WORKFLOW_SKILL_CONFLICT);
        assertThatThrownBy(() -> repository.update(
            "safe-skill", skill("safe-skill", "stale"), "outdated-revision"))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorCode())
            .isEqualTo(ErrorCode.WORKFLOW_SKILL_CONFLICT);
        assertThatThrownBy(() -> repository.update(
            "safe-skill", skill("safe-skill", "x".repeat(2048)), created.revision()))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorCode())
            .isEqualTo(ErrorCode.VALIDATION_ERROR);

        assertThat(repository.get("safe-skill").content()).contains("current");
    }

    @Test
    void rejectsAStaleRevisionAcrossRepositoryInstances() {
        FileSkillRepository first = repository();
        FileSkillRepository second = repository();
        SkillDocument original = first.create("shared-skill", skill("shared-skill", "original"));
        SkillDocument updated = second.update(
            "shared-skill", skill("shared-skill", "updated"), original.revision());

        assertThatThrownBy(() -> first.update(
            "shared-skill", skill("shared-skill", "stale"), original.revision()))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorCode())
            .isEqualTo(ErrorCode.WORKFLOW_SKILL_CONFLICT);
        assertThat(first.get("shared-skill").revision()).isEqualTo(updated.revision());
    }

    @Test
    void serviceSearchesReferencesAndBlocksReferencedDeletion() {
        FileSkillRepository repository = repository();
        repository.create("script-rewriter", skill("script-rewriter", "body"));
        SkillReferenceLookup references = new SkillReferenceLookup() {
            @Override
            public List<String> findAgentCodes(String code) {
                return List.of("episode-script-agent");
            }
        };
        WorkflowSkillService service = new WorkflowSkillService(repository, references);

        assertThat(service.list("rewrite")).singleElement()
            .satisfies(skill -> assertThat(skill.referencingAgentCodes())
                .containsExactly("episode-script-agent"));
        assertThatThrownBy(() -> service.delete("script-rewriter"))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorCode())
            .isEqualTo(ErrorCode.WORKFLOW_SKILL_IN_USE);
        assertThat(repository.exists("script-rewriter")).isTrue();
    }

    private FileSkillRepository repository() {
        return new FileSkillRepository(tempDir, 1024, new SkillDocumentParser());
    }

    private String skill(String name, String body) {
        return "---\nname: " + name + "\ndescription: test skill\n---\n\n# " + body + "\n";
    }
}
