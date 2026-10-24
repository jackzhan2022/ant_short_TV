package com.antshorttv.script;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import com.antshorttv.execution.AiExecutionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/character-prompt-similarity")
public class CharacterPromptSimilarityController {
    private final CharacterPromptSimilarityService service;
    public CharacterPromptSimilarityController(CharacterPromptSimilarityService service) { this.service = service; }

    @GetMapping
    public ApiResponse<JsonNode> status(@PathVariable long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.status(TenantRequestSupport.tenantId(request), projectId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<AiExecutionResponse> submit(@PathVariable long projectId, HttpServletRequest request) {
        String key = request.getHeader("Idempotency-Key");
        return ApiResponse.success(service.submit(TenantRequestSupport.tenantId(request), projectId,
            key == null || key.isBlank() ? UUID.randomUUID().toString() : key));
    }

    public record ApplyPrompt(String prompt) {}

    @PutMapping("/{checkId}/characters/{characterId}/prompt")
    public ApiResponse<JsonNode> apply(@PathVariable long projectId, @PathVariable long checkId,
        @PathVariable long characterId, @RequestBody ApplyPrompt body, HttpServletRequest request) {
        return ApiResponse.success(service.apply(TenantRequestSupport.tenantId(request), projectId, checkId, characterId, body.prompt()));
    }
}
