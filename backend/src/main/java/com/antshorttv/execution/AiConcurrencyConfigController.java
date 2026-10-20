package com.antshorttv.execution;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.platform.RequirePlatformPermission;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.validation.Valid;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/ai/concurrency-config")
public class AiConcurrencyConfigController {
    private final AiConcurrencyConfigService service;
    private final DataSource dataSource;

    public AiConcurrencyConfigController(AiConcurrencyConfigService service, DataSource dataSource) {
        this.service = service;
        this.dataSource = dataSource;
    }

    @GetMapping
    @RequirePlatformPermission("PLATFORM_AI_CONCURRENCY_VIEW")
    public ApiResponse<Response> get() {
        return ApiResponse.success(response(service.current()));
    }

    @PutMapping
    @RequirePlatformPermission("PLATFORM_AI_CONCURRENCY_EDIT")
    public ApiResponse<Response> save(@Valid @RequestBody AiConcurrencySettings settings) {
        return ApiResponse.success(response(service.save(settings)));
    }

    private Response response(AiConcurrencySettings settings) {
        try {
            return new Response(settings, dataSource.unwrap(HikariDataSource.class).getMaximumPoolSize());
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot read the Hikari connection pool maximum", exception);
        }
    }

    public record Response(AiConcurrencySettings settings, int databaseMaxConnections) { }
}
