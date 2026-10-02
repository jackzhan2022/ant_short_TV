package com.antshorttv.style;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.MediaPage;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/style-library")
public class StyleLibraryController {
    private final StyleLibraryService styleLibraryService;

    public StyleLibraryController(StyleLibraryService styleLibraryService) {
        this.styleLibraryService = styleLibraryService;
    }

    @GetMapping
    public ApiResponse<MediaPage<StyleLibraryResponse>> list(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer current,
        @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(styleLibraryService.list(category, keyword, current, pageSize));
    }

    @GetMapping("/categories")
    public ApiResponse<List<String>> categories() {
        return ApiResponse.success(styleLibraryService.categories());
    }

    @GetMapping("/{styleId}")
    public ApiResponse<StyleLibraryResponse> detail(@PathVariable Long styleId) {
        return ApiResponse.success(styleLibraryService.detail(styleId));
    }

    @GetMapping("/images/{externalId}")
    public ResponseEntity<Void> image(@PathVariable String externalId) {
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(styleLibraryService.image(externalId)))
            .build();
    }
}
