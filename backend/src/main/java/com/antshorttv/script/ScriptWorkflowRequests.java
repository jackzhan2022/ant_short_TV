package com.antshorttv.script;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
