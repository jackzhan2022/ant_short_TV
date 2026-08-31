package com.antshorttv.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ScriptAnalysisErrorContractTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsStaleInputsToConflictWithoutLosingTypedCodes() {
        assertStatus(ErrorCode.SCRIPT_CONTENT_CHANGED, HttpStatus.CONFLICT);
        assertStatus(ErrorCode.EPISODE_CONTENT_CHANGED, HttpStatus.CONFLICT);
        assertStatus(ErrorCode.ANALYSIS_EPISODE_SNAPSHOT_CHANGED, HttpStatus.CONFLICT);
    }

    @Test
    void mapsAmbiguousAndIncompleteAgentResultsToUnprocessableEntity() {
        assertStatus(ErrorCode.SCRIPT_ASSET_AMBIGUOUS, HttpStatus.UNPROCESSABLE_ENTITY);
        assertStatus(ErrorCode.ENTITY_MATCH_AMBIGUOUS, HttpStatus.UNPROCESSABLE_ENTITY);
        assertStatus(ErrorCode.ANALYSIS_AGENT_INCOMPLETE, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private void assertStatus(ErrorCode code, HttpStatus status) {
        var response = handler.handleBusinessException(new BusinessException(code, "safe message"));
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(code.name());
        assertThat(response.getBody().errorMessage()).isEqualTo("safe message");
    }
}
