package com.antshorttv.ai;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.platform.RequirePlatformPermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/ai/definitions")
public class EditableAiDefinitionController {
    private final EditableAiDefinitionService service;
    public EditableAiDefinitionController(EditableAiDefinitionService service) { this.service = service; }
    @GetMapping("/agents") @RequirePlatformPermission("PLATFORM_AI_AGENT_VIEW")
    public ApiResponse<List<EditableAgentResponse>> agents() { return ApiResponse.success(service.agents()); }
    @PutMapping("/agents/{code}") @RequirePlatformPermission("PLATFORM_AI_AGENT_EDIT")
    public ApiResponse<EditableAgentResponse> updateAgent(@PathVariable String code, @Valid @RequestBody EditableAgentRequest body, HttpServletRequest request) { return ApiResponse.success(service.updateAgent(code, body, request)); }
    @PostMapping("/agents/{code}/preview") @RequirePlatformPermission("PLATFORM_AI_AGENT_VIEW")
    public ApiResponse<String> previewAgent(@PathVariable String code, @RequestBody(required = false) java.util.Map<String, Object> variables) { return ApiResponse.success(service.previewAgent(code, variables)); }
    @PostMapping("/agents/{code}/publish") @RequirePlatformPermission("PLATFORM_AI_AGENT_EDIT")
    public ApiResponse<EditableAgentResponse> publishAgent(@PathVariable String code, HttpServletRequest request) { return ApiResponse.success(service.publishAgent(code, request)); }
    @PostMapping("/agents/{code}/{status}") @RequirePlatformPermission("PLATFORM_AI_AGENT_EDIT")
    public ApiResponse<EditableAgentResponse> setAgentStatus(@PathVariable String code, @PathVariable String status, HttpServletRequest request) { return ApiResponse.success(service.setAgentStatus(code, status, request)); }
    @PostMapping("/agents/{code}/rollback/{version}") @RequirePlatformPermission("PLATFORM_AI_AGENT_EDIT")
    public ApiResponse<EditableAgentResponse> rollbackAgent(@PathVariable String code, @PathVariable Integer version, HttpServletRequest request) { return ApiResponse.success(service.rollbackAgent(code, version, request)); }
    @GetMapping("/skills") @RequirePlatformPermission("PLATFORM_AI_AGENT_VIEW")
    public ApiResponse<List<EditableSkillResponse>> skills() { return ApiResponse.success(service.skills()); }
    @PutMapping("/skills/{code}") @RequirePlatformPermission("PLATFORM_AI_SKILL_EDIT")
    public ApiResponse<EditableSkillResponse> updateSkill(@PathVariable String code, @Valid @RequestBody EditableSkillRequest body, HttpServletRequest request) { return ApiResponse.success(service.updateSkill(code, body, request)); }
    @PostMapping("/skills/{code}/publish") @RequirePlatformPermission("PLATFORM_AI_SKILL_EDIT")
    public ApiResponse<EditableSkillResponse> publishSkill(@PathVariable String code, HttpServletRequest request) { return ApiResponse.success(service.publishSkill(code, request)); }
    @PostMapping("/skills/{code}/{status}") @RequirePlatformPermission("PLATFORM_AI_SKILL_EDIT")
    public ApiResponse<EditableSkillResponse> setSkillStatus(@PathVariable String code, @PathVariable String status, HttpServletRequest request) { return ApiResponse.success(service.setSkillStatus(code, status, request)); }
    @PostMapping("/skills/{code}/rollback/{version}") @RequirePlatformPermission("PLATFORM_AI_SKILL_EDIT")
    public ApiResponse<EditableSkillResponse> rollbackSkill(@PathVariable String code, @PathVariable Integer version, HttpServletRequest request) { return ApiResponse.success(service.rollbackSkill(code, version, request)); }
}
