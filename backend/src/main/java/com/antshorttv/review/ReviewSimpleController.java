package com.antshorttv.review;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import com.antshorttv.execution.AiExecutionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@com.antshorttv.rbac.RequirePermission("MENU:SCRIPT_REVIEW")
@RestController
@RequestMapping("/api/script-review")
public class ReviewSimpleController {
    private final ReviewWorkbenchService workbench;
    private final ReviewReadService reads;

    public ReviewSimpleController(ReviewWorkbenchService workbench, ReviewReadService reads) {
        this.workbench = workbench;
        this.reads = reads;
    }

    @PostMapping("/submissions")
    public ResponseEntity<ApiResponse<AiExecutionResponse>> submit(
        @Valid @RequestBody ReviewSubmissionRequest body, HttpServletRequest request
    ) {
        return ResponseEntity.accepted().body(ApiResponse.success(
            workbench.submitReview(TenantRequestSupport.tenantId(request), body)));
    }

    @GetMapping("/history")
    public ApiResponse<ReviewReadService.HistoryPage> history(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request
    ) {
        return ApiResponse.success(reads.history(TenantRequestSupport.tenantId(request), page, pageSize));
    }

    @GetMapping("/tasks/{taskId}/progress")
    public ApiResponse<ReviewReadService.Progress> progress(@PathVariable Long taskId, HttpServletRequest request) {
        return ApiResponse.success(reads.progress(TenantRequestSupport.tenantId(request), taskId));
    }

    @GetMapping("/tasks/{taskId}/report")
    public ApiResponse<ReviewReadService.Report> report(@PathVariable Long taskId, HttpServletRequest request) {
        return ApiResponse.success(reads.report(TenantRequestSupport.tenantId(request), taskId));
    }

    @GetMapping("/tasks/{taskId}/content")
    public ApiResponse<ReviewVersionResponse> content(@PathVariable Long taskId, HttpServletRequest request) {
        return ApiResponse.success(reads.content(TenantRequestSupport.tenantId(request), taskId));
    }
}

record ReviewSubmissionRequest(
    @NotBlank @Size(max = 200000) String content,
    @NotEmpty @Size(max = 13) List<@NotBlank @Size(max = 64) String> selectedDimensions,
    @NotBlank @Pattern(regexp = "[a-zA-Z0-9-]{16,64}") String requestId
) {}
