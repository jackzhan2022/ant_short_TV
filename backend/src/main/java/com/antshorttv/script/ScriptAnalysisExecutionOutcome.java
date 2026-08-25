package com.antshorttv.script;

import java.util.List;

record ScriptAnalysisExecutionOutcome(List<ScriptAnalysisCallEvidence> calls) {
    int providerCallCount() {
        return calls.size();
    }

    ScriptAnalysisCallEvidence lastCall() {
        return calls.isEmpty() ? null : calls.get(calls.size() - 1);
    }

    Long lastCallLogId() {
        return lastCall() == null ? null : lastCall().callLogId();
    }
}
