package com.antshorttv.drama;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.antshorttv.drama.WechatCodeModels.*;

@RestController
@RequestMapping("/api/team-miniapps")
public class WechatCodeController {
    private final WechatCodeService service;
    public WechatCodeController(WechatCodeService service) { this.service = service; }

    @GetMapping("/code/templates")
    public ApiResponse<List<Template>> templates(HttpServletRequest request, HttpServletResponse response) {
        noStore(response);
        return ApiResponse.success(service.templates(TenantRequestSupport.tenantId(request)));
    }

    @PostMapping("/{id}/code/commit")
    public ApiResponse<CommitResult> commit(@PathVariable Long id, @Valid @RequestBody CommitRequest body,
                                          HttpServletRequest request, HttpServletResponse response) {
        noStore(response);
        return ApiResponse.success(service.commit(TenantRequestSupport.tenantId(request), id, body));
    }

    @GetMapping("/{id}/code/trial-qrcode")
    public ResponseEntity<byte[]> trialQrCode(@PathVariable Long id, @RequestParam(required = false) String path,
                                              HttpServletRequest request, HttpServletResponse response) {
        noStore(response);
        var qr = service.trialQrCode(TenantRequestSupport.tenantId(request), id, path);
        return ResponseEntity.ok().header("Content-Type", qr.contentType()).body(qr.bytes());
    }

    @ExceptionHandler(CodeException.class)
    public ResponseEntity<ApiResponse<CommitResult>> error(CodeException exception) {
        var data = "WECHAT_COMMIT_UNKNOWN".equals(exception.code()) ? new CommitResult("UNKNOWN") : null;
        return ResponseEntity.status(exception.status()).header("Cache-Control", "no-store")
            .header("X-Content-Type-Options", "nosniff")
            .body(new ApiResponse<>(false, data, exception.code(), exception.getMessage()));
    }

    private void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }
}
