package com.antshorttv.script;

record ScriptAnalysisCallEvidence(
    Long callLogId,
    Long modelId,
    Long providerId,
    String providerRequestId,
    String transportOutcome,
    String businessOutcome
) {
}
