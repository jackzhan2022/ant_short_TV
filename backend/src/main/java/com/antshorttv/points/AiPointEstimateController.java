package com.antshorttv.points;

import com.antshorttv.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants/{tenantId}/points")
public class AiPointEstimateController {
    private final AiPointEstimateService service;

    public AiPointEstimateController(AiPointEstimateService service) { this.service = service; }

    @PostMapping("/estimate")
    public ApiResponse<AiPointEstimateService.Estimate> estimate(@PathVariable Long tenantId,
                                                               @Valid @RequestBody EstimateRequest request) {
        return ApiResponse.success(service.estimate(tenantId, request.operations()));
    }

    public record EstimateRequest(@Valid @NotEmpty @Size(max = 500) List<AiPointEstimateService.Operation> operations) {}
}
