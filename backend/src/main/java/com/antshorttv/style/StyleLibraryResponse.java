package com.antshorttv.style;

record StyleLibraryResponse(
    Long id,
    String externalId,
    String name,
    String category,
    String description,
    String imageUrl,
    String storagePath,
    Integer imageWidth,
    Integer imageHeight
) {
    static StyleLibraryResponse from(StyleLibraryEntity entity) {
        return new StyleLibraryResponse(
            entity.getId(),
            entity.getExternalId(),
            entity.getName(),
            entity.getCategory(),
            entity.getDescription(),
            entity.getImageUrl(),
            entity.getStoragePath(),
            entity.getImageWidth(),
            entity.getImageHeight()
        );
    }
}
