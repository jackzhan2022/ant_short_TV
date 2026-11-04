package com.antshorttv.style;

import com.antshorttv.common.ApiResponse;
import java.util.List;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AssetArtStyleController {
    private final AssetArtStyleService styles;
    private final AssetArtStyleImageService images;
    public AssetArtStyleController(AssetArtStyleService styles, AssetArtStyleImageService images) {
        this.styles = styles;
        this.images = images;
    }

    @GetMapping("/api/style-library/v2")
    public ApiResponse<List<StyleOption>> list() {
        return ApiResponse.success(styles.catalog().stream()
            .map(s -> new StyleOption(s.code(), s.name(), s.category(), s.description(), images.imageUrl(s.code()))).toList());
    }
    @GetMapping("/api/style-library/v2/images/{code}")
    public ResponseEntity<Void> image(@PathVariable String code) {
        return ResponseEntity.status(HttpStatus.FOUND).cacheControl(CacheControl.noStore())
            .location(URI.create(images.deliveryUrl(code))).build();
    }
    public record StyleOption(String code, String name, String category, String description, String imageUrl) {}
}
