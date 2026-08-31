package com.antshorttv.script;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record GenerateScriptRequest(
    @Size(max = 100) String title,
    @NotBlank @Size(max = 5000) String storyIdea,
    @NotBlank @Size(max = 50) String genre,
    @Min(1) @Max(200) Integer episodeCount,
    @Min(15) @Max(600) Integer duration,
    @Size(max = 1000) String mainCharacter,
    @Size(max = 500) String styleRequirement,
    @Size(max = 5000) String referenceContent
) {
}

record ExtractScriptElementsRequest(
    @NotBlank @Size(max = 32) String elementType
) {
}

record RewriteScriptRequest(
    @NotBlank @Size(max = 32) String rewriteType,
    @Size(max = 1000) String requirement,
    @Size(max = 32) String outputLength
) {
}

record SaveScriptRequest(
    @Size(max = 100) String title,
    @NotBlank @Size(max = 200000) String content,
    @Size(max = 32) String status
) {
}

record SaveEpisodeSummaryRequest(
    @NotBlank @Size(max = 20000) String summary,
    @NotNull @Size(min = 2, max = 5) java.util.List<@NotBlank @Size(max = 1000) String> highlights,
    @Size(max = 2000) String endingHook,
    @NotNull Boolean overwrite
) {
}

record RegenerateEpisodeSummaryRequest(@NotNull Boolean overwrite) {
}

record UpdateScriptElementRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 32) String roleType,
    @Size(max = 32) String gender,
    @Size(max = 32) String ageRange,
    @Size(max = 200) String identity,
    java.util.List<@Size(max = 50) String> personality,
    @Size(max = 500) String appearance,
    @Size(max = 32) String sceneType,
    @Size(max = 100) String atmosphere,
    @Size(max = 1000) String description,
    @Size(max = 300) String visualStyle,
    @Size(max = 32) String propType,
    @Size(max = 500) String plotFunction,
    @Size(max = 200) String relatedCharacter,
    @Size(max = 5000) String prompt,
    @Size(max = 32) String status
) {
}

record StoryboardBreakdownRequest(
    @Size(max = 32) String scope,
    @Min(1) @Max(200) Integer episodeNo,
    @Size(max = 200000) String selectedText
) {
}

record SaveStoryboardRequest(
    @Min(1) @Max(200) Integer episodeNo,
    @Min(1) @Max(9999) Integer shotNo,
    @Size(max = 50) String sceneNo,
    @Size(max = 50) String shotType,
    @NotBlank @Size(max = 5000) String visualDescription,
    @Size(max = 500) String characters,
    @Size(max = 5000) String actions,
    @Size(max = 5000) String dialogue,
    @Size(max = 200) String scene,
    @Size(max = 500) String props,
    @Size(max = 200) String mood,
    @Min(1) @Max(600) Integer durationSeconds,
    @Size(max = 5000) String imagePrompt,
    @Size(max = 5000) String videoPrompt,
    @Size(max = 32) String status
) {
}

record MoveStoryboardRequest(
    @Min(1) @Max(9999) Integer shotNo
) {
}

record GeneratePromptRequest(
    @NotBlank @Size(max = 32) String targetType,
    Long targetId
) {
}
