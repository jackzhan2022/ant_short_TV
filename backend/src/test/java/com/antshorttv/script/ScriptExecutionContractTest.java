package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.execution.AiExecutionResponse;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;

/**
 * Contract guard for the script migration. Every provider-facing script entry
 * point must eventually return the shared execution envelope instead of a
 * materialized workspace from the request thread.
 */
class ScriptExecutionContractTest {

    @Test
    void providerFacingScriptEndpointsExposeExecutionEnvelope() throws Exception {
        assertExecutionResponse("generate", GenerateScriptRequest.class);
        assertExecutionResponse("rewrite", RewriteScriptRequest.class);
        assertExecutionResponse("extractElements", ExtractScriptElementsRequest.class);
        assertExecutionResponse("breakdownStoryboards", StoryboardBreakdownRequest.class);
        assertExecutionResponse("generatePrompts", GeneratePromptRequest.class);
    }

    private void assertExecutionResponse(String methodName, Class<?> requestType) throws Exception {
        Method method = ScriptWorkflowController.class.getMethod(
            methodName,
            Long.class,
            requestType,
            jakarta.servlet.http.HttpServletRequest.class
        );
        ResolvableType returnType = ResolvableType.forMethodReturnType(method);
        assertThat(returnType.resolve()).isEqualTo(org.springframework.http.ResponseEntity.class);
        ResolvableType envelopeType = returnType.getGeneric(0);
        assertThat(envelopeType.resolve()).isEqualTo(com.antshorttv.common.ApiResponse.class);
        assertThat(envelopeType.getGeneric(0).resolve()).isEqualTo(AiExecutionResponse.class);
    }
}
