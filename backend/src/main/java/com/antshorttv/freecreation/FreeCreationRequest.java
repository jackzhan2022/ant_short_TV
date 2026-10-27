package com.antshorttv.freecreation;

import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record FreeCreationRequest(
    @Size(max=100) String requestId,
    @NotNull @Pattern(regexp="IMAGE|VIDEO") String mediaType,
    @NotNull @Positive Long modelId,
    @NotBlank @Size(max=20000) String prompt,
    Map<String,Object> parameters,
    @Valid @Size(max=50) List<@NotNull Reference> references
) {
    public record Reference(@NotNull @Pattern(regexp="IMAGE|VIDEO|AUDIO") String kind,
        @Size(max=200) String uploadSessionToken, @Positive Long referenceId,
        @Valid ProjectImage projectImage) {
        public Reference(String kind, String uploadSessionToken, Long referenceId) {
            this(kind,uploadSessionToken,referenceId,null);
        }
    }
    public record ProjectImage(@NotNull @Positive Long projectId, @NotNull @Positive Long resultId) {}
}
