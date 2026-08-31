package com.antshorttv.workflowagent.tool;

import java.util.List;

public record ReviewToolScope(
    Long reviewProjectId,
    Long versionId,
    Long snapshotId,
    Long unitId,
    Integer attemptNo,
    String phase,
    List<String> selectedDimensions
) {
    public ReviewToolScope {
        selectedDimensions = selectedDimensions == null ? List.of() : List.copyOf(selectedDimensions);
    }
}
