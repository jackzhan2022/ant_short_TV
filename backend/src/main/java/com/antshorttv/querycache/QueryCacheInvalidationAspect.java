package com.antshorttv.querycache;

import com.antshorttv.execution.AiExecutionClaim;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.antshorttv.script.ScriptEpisodeSummaryDocument;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static com.antshorttv.querycache.QueryResultCache.Region.*;
import static com.antshorttv.querycache.QueryResultCache.Scope.*;

/** Successful writes invalidate only their data owners, after the surrounding transaction commits. */
@Aspect
@Component
public class QueryCacheInvalidationAspect {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(QueryCacheInvalidationAspect.class);
    private final QueryResultCache cache;
    @Autowired(required=false)
    private AiExecutionTaskMapper taskMapper;

    public QueryCacheInvalidationAspect(QueryResultCache cache) { this.cache=cache; }

    // Public model choices are platform-wide, so configuration changes intentionally invalidate all choices.
    @AfterReturning("execution(public * com.antshorttv.ai.PlatformAiManagementService.*(..)) && @annotation(org.springframework.transaction.annotation.Transactional)")
    public void modelsChanged() { cache.invalidateAfterCommit(MODELS); }

    @AfterReturning("(execution(public * com.antshorttv.script.ScriptWorkflowService.*(..)) || execution(public * com.antshorttv.script.ScriptEpisodeService.*(..)) || execution(public * com.antshorttv.script.ScriptAnalysisTaskService.*(..))) && @annotation(org.springframework.transaction.annotation.Transactional)")
    public void scriptChanged(JoinPoint point) {
        if(!cache.isEnabled()) return;
        Object[] args=point.getArgs();
        Long tenantId=(Long)args[0],projectId=(Long)args[1];
        cache.invalidateProjectAfterCommit(tenantId,projectId,SCRIPT,ANALYSIS);
        if(tenantId!=null) cache.invalidateAfterCommit(tenant(tenantId),TASKS);
    }

    @AfterReturning(value="execution(public * com.antshorttv.script.ScriptEpisodeSummaryRepository.upsert(..)) && args(document)",argNames="document")
    public void summaryChanged(ScriptEpisodeSummaryDocument document) {
        cache.invalidateProjectAfterCommit(document.tenantId(),document.projectId(),SCRIPT);
    }

    @AfterReturning(value="execution(public * com.antshorttv.execution.AiExecutionClaimService.*(..)) && @annotation(org.springframework.transaction.annotation.Transactional) && !execution(* *.heartbeat(..)) && !execution(* *.recoverExpiredClaims(..))",returning="result")
    public void executionChanged(JoinPoint point,Object result) {
        if(!cache.isEnabled() || taskMapper==null) return;
        if("claim".equals(point.getSignature().getName()) && result==null) return;
        Object first=point.getArgs()[0];
        Long executionId=first instanceof AiExecutionClaim claim ? claim.executionId() : (Long)first;
        // Resolve scope only for actual state changes, never for polling or failed admission.
        try {
            var task=taskMapper.selectById(executionId);
            if(task!=null && task.tenantId!=null) {
                cache.invalidateAfterCommit(tenant(task.tenantId),TASKS);
                cache.invalidateProjectAfterCommit(task.tenantId,task.projectId,ANALYSIS);
            }
        } catch(org.springframework.dao.DataAccessException unavailable) {
            // The business write has succeeded. The short display TTL remains the fallback.
            log.warn("Could not resolve execution cache scope; display TTL will refresh it");
        }
    }
}
