package com.antshorttv.operations.telemetry;

import com.antshorttv.execution.AiExecutionTaskEntity;
import java.util.*;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

/** Capture immutable lightweight values while visible, enqueue only after business commit. */
@Aspect
@Component
public class OperationsExecutionSnapshotAspect {
    private final OperationsTelemetry telemetry;
    public OperationsExecutionSnapshotAspect(OperationsTelemetry telemetry){this.telemetry=telemetry;}
    @AfterReturning(pointcut="target(com.antshorttv.execution.AiExecutionTaskMapper)",returning="result")
    public void observed(JoinPoint point,Object result) {
        try {
            if(result instanceof AiExecutionTaskEntity entity)capture(entity);
            else if(result instanceof Collection<?> rows)for(Object row:rows)if(row instanceof AiExecutionTaskEntity entity)capture(entity);
            if(result instanceof Number changed && changed.longValue()>0)for(Object argument:point.getArgs())if(argument instanceof AiExecutionTaskEntity entity)capture(entity);
        } catch(RuntimeException ignored){telemetry.dropped.incrementAndGet();}
    }
    @AfterReturning("execution(public * com.antshorttv.execution.AiExecutionClaimService.*(..))")
    public void changed(JoinPoint point) {
        try {
            Object[] arguments=point.getArgs();if(arguments.length==0)return;
            if(arguments[0] instanceof Long id)telemetry.captureExecution(id);
            else if(arguments[0] instanceof com.antshorttv.execution.AiExecutionClaim claim)telemetry.captureExecution(claim.executionId());
        }catch(RuntimeException ignored){telemetry.dropped.incrementAndGet();}
    }
    private void capture(AiExecutionTaskEntity entity) {
        // MyBatis also returns deliberately partial entities for admission/cursor queries.
        if(entity.id==null || entity.executionVersion==null || entity.status==null || entity.createdAt==null)return;
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("id",entity.id);row.put("execution_version",entity.executionVersion);row.put("tenant_id",entity.tenantId);row.put("project_id",entity.projectId);
        row.put("model_id",entity.resolvedModelId==null?entity.requestedModelId:entity.resolvedModelId);row.put("scene",entity.scene);row.put("task_type",entity.taskType);
        row.put("status",entity.status);row.put("phase",entity.phase);row.put("created_at",entity.createdAt);row.put("started_at",entity.startedAt);row.put("completed_at",entity.completedAt);
        row.put("next_run_at",entity.nextRunAt);row.put("heartbeat_at",entity.heartbeatAt);row.put("claim_expires_at",entity.claimExpiresAt);
        row.put("point_settlement_status",entity.pointSettlementStatus);row.put("error_code",entity.errorCode);
        // Sanitization occurs before queue insertion, including mutable source exception strings.
        row.put("error_message",OperationsTelemetry.cleanDimension(entity.errorMessage,500));row.put("trace_id",OperationsTelemetry.cleanDimension(entity.traceId,128));
        telemetry.captureExecutionSnapshot(row);
    }
}
