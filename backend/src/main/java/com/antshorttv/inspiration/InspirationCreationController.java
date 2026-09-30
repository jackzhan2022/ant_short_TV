package com.antshorttv.inspiration;

import com.antshorttv.common.ApiResponse;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inspiration-creations")
public class InspirationCreationController {
    private final InspirationCreationService service;

    public InspirationCreationController(InspirationCreationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<InspirationCreationPageResponse> list(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "8") Integer pageSize
    ) {
        return ApiResponse.success(service.list(page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<InspirationCreationDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Void> file(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(service.file(id).url()))
            .build();
    }

    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<Void> thumbnail(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(service.thumbnail(id).url()))
            .build();
    }
}
