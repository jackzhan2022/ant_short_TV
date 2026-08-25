package com.antshorttv.ai;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.common.ErrorCode;
import com.antshorttv.aiimage.AiImageTaskController;
import com.antshorttv.rbac.RequireProjectPermission;
import com.antshorttv.video.AiVideoTaskController;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "com.antshorttv", importOptions = ImportOption.DoNotIncludeTests.class)
class LegacyAiConfigurationArchitectureTest {

    @ArchTest
    static final ArchRule productionCodeDoesNotDependOnLegacyConfiguration = noClasses()
        .should().dependOnClassesThat().haveNameMatching("com\\.antshorttv\\.ai\\.AiServiceConfig.*")
        .because("platform Provider and Model records are the only AI configuration authority");

    @ArchTest
    static final ArchRule businessServicesDoNotOwnProviderHttp = noClasses()
        .that().haveSimpleName("AiImageTaskService")
        .or().haveSimpleName("AiVideoTaskService")
        .or().haveSimpleName("ShotProductionService")
        .should().dependOnClassesThat().resideInAPackage("java.net.http")
        .because("provider transport belongs to registered adapters behind AiInvocationService");

    @Test
    void providerBackedContractsUseModelIdentity() throws ClassNotFoundException {
        assertThat(componentNames("com.antshorttv.aiimage.CreateAiImageTaskRequest")).contains("modelId").doesNotContain("serviceConfigId");
        assertThat(componentNames("com.antshorttv.aiimage.AiImageTaskResponse")).contains("modelId").doesNotContain("serviceConfigId");
        assertThat(componentNames("com.antshorttv.video.CreateAiVideoTaskRequest")).contains("modelId").doesNotContain("serviceConfigId");
        assertThat(componentNames("com.antshorttv.video.AiVideoTaskResponse")).contains("modelId").doesNotContain("serviceConfigId");
    }

    @Test
    void localVoicePlaceholderHasNoProviderConfigurationIdentity() throws ClassNotFoundException {
        assertThat(componentNames("com.antshorttv.shot.CreateAiVoiceTaskRequest")).doesNotContain("serviceConfigId", "modelId");
        assertThat(componentNames("com.antshorttv.shot.AiVoiceTaskResponse")).doesNotContain("serviceConfigId", "modelId");
    }

    @Test
    void legacyConfigurationErrorCodeIsRetired() {
        assertThat(Arrays.stream(ErrorCode.values()).map(Enum::name))
            .doesNotContain("AI_SERVICE_CONFIG_NOT_FOUND");
    }

    @Test
    void providerBackedCreationRequiresWorkflowAndAiUsagePermissions() throws Exception {
        assertRequiredPermissions(AiImageTaskController.class, "create", "AI_IMAGE_TASK:CREATE", "AI_SERVICE:USE");
        assertRequiredPermissions(AiImageTaskController.class, "regenerate", "AI_IMAGE_TASK:CREATE", "AI_SERVICE:USE");
        assertRequiredPermissions(AiVideoTaskController.class, "create", "AI_VIDEO_TASK:CREATE", "AI_SERVICE:USE");
        assertRequiredPermissions(AiVideoTaskController.class, "regenerate", "AI_VIDEO_TASK:CREATE", "AI_SERVICE:USE");
    }

    private void assertRequiredPermissions(Class<?> controllerType, String methodName, String... expected) throws Exception {
        RequireProjectPermission annotation = Arrays.stream(controllerType.getDeclaredMethods())
            .filter(method -> method.getName().equals(methodName))
            .findFirst()
            .orElseThrow()
            .getAnnotation(RequireProjectPermission.class);
        Object configured = annotation.annotationType().getMethod("value").invoke(annotation);
        assertThat(configured).isInstanceOf(String[].class);
        assertThat((String[]) configured).containsExactlyInAnyOrder(expected);
    }

    private List<String> componentNames(String className) throws ClassNotFoundException {
        Class<?> recordType = Class.forName(className);
        return Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}
