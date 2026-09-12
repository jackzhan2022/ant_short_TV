package com.antshorttv.workflowagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.antshorttv.review.ReviewToolReadService;
import org.springframework.stereotype.Service;

@Service
public class ReviewToolDataService {
    private final ReviewToolReadService reads;

    public ReviewToolDataService(ReviewToolReadService reads) {
        this.reads = reads;
    }

    public JsonNode readContext(ToolExecutionContext context) { return reads.readContext(context); }
    public JsonNode readContent(ToolExecutionContext context, JsonNode arguments) { return reads.readContent(context, arguments); }
}
