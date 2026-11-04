package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AssetIdentityAmbiguityExceptionTest {
    @Test void boundsTotalFeedbackAndPreservesEveryOmittedIdentityForDeferral() {
        var json = new ObjectMapper();
        var conflicts = json.createArrayNode();
        for (int i = 0; i < 100; i++) {
            var conflict = conflicts.addObject().put("assetType", "CHARACTER").put("localKey", "c" + i);
            conflict.putArray("candidates").addObject().put("assetKey", "c_" + i).put("evidence", "证".repeat(20_000));
        }
        var result = new AssetIdentityAmbiguityException(conflicts).conflicts();
        assertThat(result.toString().length()).isLessThanOrEqualTo(64_000);
        for (int i = 0; i < 100; i++) {
            String key = "c" + i;
            boolean present = false;
            for (var conflict : result) {
                present |= key.equals(conflict.path("localKey").asText());
                for (var deferred : conflict.path("deferIdentities").path("CHARACTER"))
                    present |= key.equals(deferred.asText());
            }
            assertThat(present).as("identity %s is still actionable", key).isTrue();
        }
    }
}
