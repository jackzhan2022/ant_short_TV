package com.antshorttv.monitoring;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.security.CurrentPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {
    public record FrontendEvent(
        @NotBlank @Pattern(regexp = "FRONTEND_(RENDER|RUNTIME|PROMISE|REQUEST)") String source,
        @Size(max = 100) String code,
        @Size(max = 300) String location,
        @NotBlank @Size(max = 500) String message,
        @Size(max = 800) String stack
    ) {}

    private final MonitoringBuffer buffer;
    private final CurrentPrincipal principal;

    public MonitoringController(MonitoringBuffer buffer, CurrentPrincipal principal) {
        this.buffer = buffer;
        this.principal = principal;
    }

    @PostMapping("/frontend-events")
    public ApiResponse<Void> collect(@Valid @RequestBody FrontendEvent event, HttpServletRequest request) {
        Long userId = principal.require().userId();
        buffer.record(new MonitoringEvent(Instant.now(), event.source(), event.code(), event.location(),
            event.message(), event.stack(), userId,
            MonitoringApiExceptionResolver.tenantHeader(request.getHeader("X-Tenant-Id")), null, null));
        return ApiResponse.ok();
    }
}
