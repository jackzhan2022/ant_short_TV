package com.antshorttv.workflowagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowAgentInfrastructureContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesTypedValidatedRuntimeConfigurationWithSafeDefaults() throws Exception {
        Class<?> type = Class.forName("com.antshorttv.workflowagent.WorkflowAgentProperties");
        Object properties = type.getConstructor().newInstance();

        assertThat(invoke(properties, "getSkillRoot")).isEqualTo(Path.of("skills"));
        assertThat(invoke(properties, "getMaxSkillFileBytes")).isEqualTo(1_048_576L);
        assertThat(invoke(properties, "getMaxSteps")).isEqualTo(20);
        assertThat(invoke(properties, "getRunTimeoutSeconds")).isEqualTo(300L);
        assertThat(invoke(properties, "getMaxLogPayloadBytes")).isEqualTo(262_144L);
    }

    @Test
    void declaresStablePermissionConstantsForTheIndependentModules() throws Exception {
        Class<?> type = Class.forName("com.antshorttv.workflowagent.WorkflowAiPermissions");

        assertThat(type.getField("AGENT_VIEW").get(null)).isEqualTo("PLATFORM_AI_WORKFLOW_AGENT_VIEW");
        assertThat(type.getField("AGENT_EDIT").get(null)).isEqualTo("PLATFORM_AI_WORKFLOW_AGENT_EDIT");
        assertThat(type.getField("SKILL_VIEW").get(null)).isEqualTo("PLATFORM_AI_WORKFLOW_SKILL_VIEW");
        assertThat(type.getField("SKILL_EDIT").get(null)).isEqualTo("PLATFORM_AI_WORKFLOW_SKILL_EDIT");
    }

    @Test
    void applicationConfigurationDocumentsEveryRuntimeLimit() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(yaml).contains("workflow-agent:");
        assertThat(yaml).contains("skill-root: ${AI_WORKFLOW_SKILL_ROOT:skills}");
        assertThat(yaml).contains("max-skill-file-bytes: ${AI_WORKFLOW_MAX_SKILL_FILE_BYTES:1048576}");
        assertThat(yaml).contains("max-steps: ${AI_WORKFLOW_MAX_STEPS:20}");
        assertThat(yaml).contains("run-timeout-seconds: ${AI_WORKFLOW_RUN_TIMEOUT_SECONDS:300}");
        assertThat(yaml).contains("max-log-payload-bytes: ${AI_WORKFLOW_MAX_LOG_PAYLOAD_BYTES:262144}");
    }

    @Test
    void productionRequiresAnAbsoluteWritableSkillRootAndVerifiesAtomicReplacement() throws Exception {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            WorkflowSkillStorageHealth.validateStorage(Path.of("skills"), true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("absolute");

        Path root = temporaryDirectory.resolve("persistent-skills").toAbsolutePath();
        assertThat(WorkflowSkillStorageHealth.validateStorage(root, true)).isEqualTo(root.normalize());
        assertThat(Files.isDirectory(root)).isTrue();
        try (var files = Files.list(root)) {
            assertThat(files).isEmpty();
        }
    }

    private Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }
}
