package com.antshorttv.points;

import com.antshorttv.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TeamPointController {

    private final TeamPointService teamPointService;

    public TeamPointController(TeamPointService teamPointService) {
        this.teamPointService = teamPointService;
    }

    @GetMapping("/tenants/{tenantId}/points/account")
    public ApiResponse<TeamPointAccountResponse> account(@PathVariable Long tenantId) {
        return ApiResponse.success(teamPointService.account(tenantId));
    }

    @PostMapping("/tenants/{tenantId}/points/adjust")
    public ApiResponse<TeamPointAccountResponse> adjust(
        @PathVariable Long tenantId,
        @Valid @RequestBody TeamPointAdjustmentRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(teamPointService.adjust(tenantId, request, servletRequest));
    }

    @GetMapping("/tenants/{tenantId}/points/transactions")
    public ApiResponse<TeamPointTransactionPageResponse> transactions(
        @PathVariable Long tenantId,
        @RequestParam(defaultValue = "1") Integer current,
        @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        return ApiResponse.success(teamPointService.transactions(tenantId, current, pageSize));
    }
}
