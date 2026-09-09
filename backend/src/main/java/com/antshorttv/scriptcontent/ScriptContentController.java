package com.antshorttv.scriptcontent;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/script-content")
public class ScriptContentController {

    private final ScriptContentParser parser;

    public ScriptContentController(ScriptContentParser parser) {
        this.parser = parser;
    }

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ScriptContentParseResponse> parse(
        @RequestPart("file") MultipartFile file,
        HttpServletRequest request
    ) {
        TenantRequestSupport.tenantId(request);
        return ApiResponse.success(new ScriptContentParseResponse(
            Optional.ofNullable(file.getOriginalFilename()).orElse(""),
            parser.parse(file)
        ));
    }
}
