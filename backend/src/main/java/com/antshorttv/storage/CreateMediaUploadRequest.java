package com.antshorttv.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateMediaUploadRequest(
    @Positive Long projectId,
    @NotBlank @Size(max = 255) String fileName,
    @NotBlank @Size(max = 128) String contentType,
    @PositiveOrZero Long fileSize
) {
}
