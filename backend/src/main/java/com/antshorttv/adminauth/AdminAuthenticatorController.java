package com.antshorttv.adminauth;

import com.antshorttv.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/admin-authenticator")
public class AdminAuthenticatorController {
    private final AdminAuthenticatorService service;
    public AdminAuthenticatorController(AdminAuthenticatorService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<ApiResponse<AdminAuthenticatorService.Status>> status() { return noStore(service.status()); }

    @PostMapping("/enrollment")
    public ResponseEntity<ApiResponse<AdminAuthenticatorService.Enrollment>> enroll(@Valid @RequestBody PasswordRequest request) {
        return noStore(service.enroll(request.password()));
    }

    @PostMapping("/confirmation")
    public ResponseEntity<ApiResponse<Boolean>> confirm(@Valid @RequestBody CodeRequest request) {
        service.confirm(request.code());
        return noStore(true);
    }

    private <T> ResponseEntity<ApiResponse<T>> noStore(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }
    public record PasswordRequest(@NotBlank @Size(max=200) String password) {}
    public record CodeRequest(@NotBlank @Size(max=20) String code) {}
}
