package com.antshorttv.desktop;

import java.time.LocalDateTime;
import java.util.List;

public final class DesktopSourceModels {
    private DesktopSourceModels() {}
    public record SyncAsset(String sourceAssetId,String sourceParentId,String name,String type) {}
    public record SyncRequest(String connectionId,String sourceTeamId,String sourceProjectId,String sourceScriptId,
                              String name,Boolean complete,List<SyncAsset> assets) {}
    public record SourceProject(Long id,String connectionId,String name,String sourceTeamId,String sourceProjectId,
                                String sourceScriptId,Long targetProjectId,String targetProjectName,boolean active,LocalDateTime syncedAt) {}
    public record SourceAsset(Long id,String sourceAssetId,String sourceParentId,String name,String type,boolean active,
                              Long targetAssetId,Long targetVariantId,String targetName,String matchStatus,boolean hasPrompt) {}
    public record Candidate(Long assetId,Long variantId,String name,String type,String parentName,boolean hasPrompt) {}
    public record Prompt(Long sourceAssetId,String name,String type,String projectName,Long targetProjectId,
                         Long targetAssetId,Long targetVariantId,String prompt,Long sourceProjectId,
                         String sourceProjectName,String sourceParentName) {}
    public record BindingRequest(Long projectId) {}
    public record MatchRequest(Long assetId,Long variantId) {}
    public record PromptRequest(List<Long> assetIds) {}
}
