package com.antshorttv.storage;

import com.antshorttv.common.ApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media-processing/callbacks/tencent-ci")
public class MediaProcessingCallbackController {
    private final CloudInfiniteProcessingService service;

    public MediaProcessingCallbackController(CloudInfiniteProcessingService service) {
        this.service = service;
    }

    @PostMapping("/{token}")
    public ApiResponse<Void> callback(
        @PathVariable String token,
        @RequestBody TencentCiTaskCallback body
    ) {
        service.handleCallback(token, body);
        return ApiResponse.ok();
    }
}
