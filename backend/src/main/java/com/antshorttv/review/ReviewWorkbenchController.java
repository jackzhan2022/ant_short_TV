package com.antshorttv.review;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/script-review")
public class ReviewWorkbenchController {

    private final ReviewWorkbenchService reviewWorkbenchService;

    public ReviewWorkbenchController(ReviewWorkbenchService reviewWorkbenchService) {
        this.reviewWorkbenchService = reviewWorkbenchService;
    }

    @GetMapping("/projects")
    public ApiResponse<List<ReviewProjectSummaryResponse>> projects(HttpServletRequest request) {
        return ApiResponse.success(reviewWorkbenchService.listProjects(tenantId(request)));
    }

    @PostMapping(value = "/projects", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ReviewProjectDetailResponse> importProject(
        @RequestPart(value = "mainProjectId", required = false) Long mainProjectId,
        @RequestPart(value = "file", required = false) MultipartFile file,
        @RequestPart(value = "content", required = false) String content,
        @RequestPart(value = "name", required = false) String name,
        HttpServletRequest request
    ) {
        return ApiResponse.success(reviewWorkbenchService.importProject(tenantId(request), mainProjectId, name, file, content));
    }

    @PutMapping("/projects/{reviewProjectId}/binding")
    public ApiResponse<ReviewProjectDetailResponse> bindProject(
        @PathVariable Long reviewProjectId,
        @Valid @RequestBody BindReviewProjectRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(reviewWorkbenchService.bindProject(tenantId(request), reviewProjectId, body));
    }

    @GetMapping("/projects/{projectId}")
    public ApiResponse<ReviewProjectDetailResponse> project(
        @PathVariable Long projectId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(reviewWorkbenchService.detailProject(tenantId(request), projectId));
    }

    @PutMapping("/projects/{projectId}/versions")
    public ApiResponse<ReviewVersionResponse> saveVersion(
        @PathVariable Long projectId,
        @Valid @RequestBody SaveReviewVersionRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(reviewWorkbenchService.saveVersion(tenantId(request), projectId, body));
    }

    @GetMapping("/projects/{projectId}/versions/{versionId}/history")
    public ApiResponse<ReviewVersionHistoryResponse> versionHistory(
        @PathVariable Long projectId,
        @PathVariable Long versionId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(reviewWorkbenchService.versionHistory(tenantId(request), projectId, versionId));
    }

    @PostMapping("/projects/{projectId}/tasks")
    public ApiResponse<ReviewTaskResponse> createTask(
        @PathVariable Long projectId,
        @Valid @RequestBody CreateReviewTaskRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(reviewWorkbenchService.createTask(tenantId(request), projectId, body));
    }

    @GetMapping("/projects/{projectId}/tasks")
    public ApiResponse<List<ReviewTaskResponse>> tasks(
        @PathVariable Long projectId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(reviewWorkbenchService.listTasks(tenantId(request), projectId));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<ReviewTaskResponse> task(
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(reviewWorkbenchService.taskDetail(tenantId(request), taskId));
    }

    @PutMapping("/tasks/{taskId}/config")
    public ApiResponse<ReviewTaskResponse> updateTaskConfig(
        @PathVariable Long taskId,
        @Valid @RequestBody UpdateReviewTaskRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(reviewWorkbenchService.updateTaskConfig(tenantId(request), taskId, body));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public ApiResponse<ReviewTaskResponse> cancelTask(
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(reviewWorkbenchService.cancelTask(tenantId(request), taskId));
    }

    @PostMapping("/tasks/{taskId}/retry")
    public ApiResponse<ReviewTaskResponse> retryTask(
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(reviewWorkbenchService.retryTask(tenantId(request), taskId));
    }

    @PostMapping("/tasks/{taskId}/batch-repair")
    public ApiResponse<ReviewTaskResponse> batchRepair(
        @PathVariable Long taskId,
        @Valid @RequestBody BatchRepairReviewRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(reviewWorkbenchService.batchRepair(tenantId(request), taskId, body));
    }

    @PostMapping("/issues/{issueId}/resolve")
    public ApiResponse<ReviewIssueResponse> resolveIssue(
        @PathVariable Long issueId,
        @Valid @RequestBody(required = false) MarkReviewIssueResolvedRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(reviewWorkbenchService.markIssueResolved(tenantId(request), issueId, body));
    }

    @PostMapping("/projects/{projectId}/rollback")
    public ApiResponse<ReviewVersionResponse> rollback(
        @PathVariable Long projectId,
        @Valid @RequestBody RollbackReviewVersionRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(reviewWorkbenchService.rollbackVersion(tenantId(request), projectId, body));
    }

    @PostMapping("/projects/{projectId}/exports")
    public ApiResponse<ReviewExportRecordResponse> export(
        @PathVariable Long projectId,
        @Valid @RequestBody ExportReviewReportRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(reviewWorkbenchService.exportReport(tenantId(request), projectId, body));
    }

    @GetMapping("/exports/{fileName}")
    public ResponseEntity<Resource> downloadExport(
        @PathVariable String fileName,
        HttpServletRequest request
    ) {
        Resource resource = reviewWorkbenchService.downloadExport(tenantId(request), fileName);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(resource);
    }

    private Long tenantId(HttpServletRequest request) {
        return TenantRequestSupport.tenantId(request);
    }
}
