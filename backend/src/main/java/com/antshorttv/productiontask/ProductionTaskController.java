package com.antshorttv.productiontask;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.aiimage.AiImageTaskService;
import com.antshorttv.video.AiVideoTaskService;
import com.antshorttv.review.ReviewWorkbenchService;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.execution.AiExecutionAccessService;
import com.antshorttv.video.VideoDecompositionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants/{tenantId}/production-tasks")
public class ProductionTaskController {
    private final ProductionTaskService service;
    private final AiImageTaskService images;
    private final AiVideoTaskService videos;
    private final ReviewWorkbenchService reviews;
    private final AiExecutionService executions;
    private final AiExecutionAccessService executionAccess;
    private final VideoDecompositionService decomposition;
    private final ObjectMapper mapper;
    public ProductionTaskController(ProductionTaskService service,AiImageTaskService images,AiVideoTaskService videos,ReviewWorkbenchService reviews,
        AiExecutionService executions,AiExecutionAccessService executionAccess,VideoDecompositionService decomposition,ObjectMapper mapper) {
        this.service=service;this.images=images;this.videos=videos;this.reviews=reviews;
        this.executions=executions;this.executionAccess=executionAccess;this.decomposition=decomposition;this.mapper=mapper;
    }
    @GetMapping public ApiResponse<ProductionTaskService.Page> list(@PathVariable long tenantId,@ModelAttribute ProductionTaskQuery query) {
        return ApiResponse.success(service.list(tenantId,query));
    }
    @GetMapping("/summary") public ApiResponse<ProductionTaskService.Summary> summary(@PathVariable long tenantId,@ModelAttribute ProductionTaskQuery query) {
        return ApiResponse.success(service.summary(tenantId,query));
    }
    @GetMapping("/{taskKey}") public ApiResponse<Map<String,Object>> detail(@PathVariable long tenantId,@PathVariable String taskKey) {
        return ApiResponse.success(service.detail(tenantId,taskKey));
    }
    @GetMapping("/{taskKey}/content") public ApiResponse<Map<String,Object>> content(@PathVariable long tenantId,@PathVariable String taskKey) {
        return ApiResponse.success(service.content(tenantId,taskKey));
    }
    @GetMapping("/{taskKey}/content/{sectionKey}") public ApiResponse<Map<String,Object>> contentSection(@PathVariable long tenantId,@PathVariable String taskKey,
        @PathVariable String sectionKey,@RequestParam(defaultValue="0") int offset) {
        return ApiResponse.success(service.contentSection(tenantId,taskKey,sectionKey,offset));
    }
    @GetMapping("/{taskKey}/children") public ApiResponse<ProductionTaskService.Page> children(@PathVariable long tenantId,@PathVariable String taskKey,@ModelAttribute ProductionTaskQuery query) {
        return ApiResponse.success(service.children(tenantId,taskKey,query));
    }
    @PostMapping("/{taskKey}/{action}") public ApiResponse<Map<String,Object>> control(@PathVariable long tenantId,@PathVariable String taskKey,
        @PathVariable String action,HttpServletRequest request) {
        Map<String,Object> task=service.detail(tenantId,taskKey);
        if(!List.of("cancel","retry","regenerate").contains(action) || !((List<?>)task.get("allowedActions")).contains(action.toUpperCase(java.util.Locale.ROOT))) throw ProductionTaskService.denied();
        long id=Long.parseLong(taskKey.split(":")[1]); Long project=(Long)task.get("projectId");
        switch((String)task.get("type")) {
            case "IMAGE" -> {
                if(action.equals("regenerate")) {
                    String key=request.getHeader("Idempotency-Key");
                    if(key==null || key.isBlank() || key.length()>128) throw ProductionTaskService.invalid();
                    // Namespace domain idempotency by source; a key cannot return another user's task.
                    String scoped="ptc-"+UUID.nameUUIDFromBytes((tenantId+":"+taskKey+":"+key).getBytes(StandardCharsets.UTF_8));
                    HttpServletRequest wrapped=new HttpServletRequestWrapper(request) {
                        @Override public String getHeader(String name) {return "Idempotency-Key".equalsIgnoreCase(name)?scoped:super.getHeader(name);}
                    };
                    Object created=images.regenerate(tenantId,project,id,wrapped);
                    long newId=mapper.valueToTree(created).path("id").asLong();
                    return ApiResponse.success(service.detail(tenantId,"IMAGE:"+newId));
                }
                images.cancel(tenantId,project,id,request);
            }
            case "VIDEO" -> videos.cancel(tenantId,project,id,request);
            case "REVIEW" -> {if(action.equals("cancel")) reviews.cancelTask(tenantId,id);else reviews.retryTask(tenantId,id);}
            case "SCRIPT_OPERATION", "STORYBOARD_ITEM" -> {
                var access=service.access(tenantId);
                Long executionId=ProductionTaskService.number(service.requireRow(access,taskKey),"execution_id");
                executionAccess.requireControl(tenantId,executionId);
                if(action.equals("cancel")) executions.cancel(executionId);else executions.retry(executionId);
            }
            case "VIDEO_EPISODE" -> decomposition.retry(tenantId,id,null);
            default -> throw ProductionTaskService.denied();
        }
        return ApiResponse.success(service.detail(tenantId,taskKey));
    }
}
