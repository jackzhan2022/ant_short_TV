package com.antshorttv.freecreation;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import com.antshorttv.rbac.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequirePermission("MENU:FREE_CREATION")
@RestController
public class FreeCreationPromptPolishController {
    private final FreeCreationPromptPolishService service;

    public FreeCreationPromptPolishController(FreeCreationPromptPolishService service) {
        this.service = service;
    }

    public record PolishRequest(
        @NotNull @Pattern(regexp = "IMAGE|VIDEO") String mediaType,
        @NotBlank @Size(max = 2000) String prompt
    ) {}
    public record PolishResponse(String prompt) {}

    @PostMapping("/api/free-creations/polish-prompt")
    public ApiResponse<PolishResponse> polish(HttpServletRequest request, @Valid @RequestBody PolishRequest body) {
        return ApiResponse.success(new PolishResponse(
            service.polish(TenantRequestSupport.tenantId(request), body.mediaType(), body.prompt())));
    }
}
