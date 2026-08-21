package com.antshorttv.inspiration;

import java.util.Map;

public record InspirationCreationImportRequest(
    String listUrl,
    String detailUrlTemplate,
    Map<String, String> headers
) {
}
