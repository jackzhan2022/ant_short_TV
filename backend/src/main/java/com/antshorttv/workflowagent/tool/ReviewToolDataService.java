package com.antshorttv.workflowagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.antshorttv.review.ReviewToolReadService;
import com.antshorttv.review.ReviewToolWriteService;
import org.springframework.stereotype.Service;

@Service
public class ReviewToolDataService {
    private final ReviewToolReadService reads;
    private final ReviewToolWriteService writes;

    public ReviewToolDataService(ReviewToolReadService reads, ReviewToolWriteService writes) {
        this.reads = reads;
        this.writes = writes;
    }

    public JsonNode readContext(ToolExecutionContext context) { return reads.readContext(context); }
    public JsonNode readContent(ToolExecutionContext context, JsonNode arguments) { return reads.readContent(context, arguments); }
    public JsonNode readHistory(ToolExecutionContext context, JsonNode arguments) { return reads.readHistory(context, arguments); }
    public JsonNode saveUnitResult(ToolExecutionContext context, JsonNode arguments) { return writes.saveUnitResult(context, arguments); }
    public JsonNode readUnitResults(ToolExecutionContext context, JsonNode arguments) { return writes.readUnitResults(context, arguments); }
    public JsonNode saveResult(ToolExecutionContext context, JsonNode arguments) { return writes.saveResult(context, arguments); }
}
