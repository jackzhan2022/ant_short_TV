package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.*;
import com.antshorttv.workflowagent.tool.ToolExecutionContext;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetExtractionCoordination {
    private final JdbcTemplate jdbc;
    public AssetExtractionCoordination(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    private Map<String,Object> lock(Long tenant,Long project,Long script) {
        jdbc.update("""
            insert into script_asset_extraction_owner(tenant_id,project_id,script_id,updated_at)
            values (?,?,?,now()) on duplicate key update script_id=script_id
            """,tenant,project,script);
        return jdbc.queryForMap("""
            select execution_id,execution_version,attempt_id,request_fingerprint
            from script_asset_extraction_owner where tenant_id=? and project_id=? and script_id=? for update
            """,tenant,project,script);
    }
    private boolean active(Object execution) {
        return execution!=null && !jdbc.queryForList("""
            select id from ai_execution_task where id=? and status in ('PENDING','RUNNING') for update
            """,Long.class,execution).isEmpty();
    }

    @Transactional
    public Long admit(Long tenant,Long project,Long script,String fingerprint) {
        var owner=lock(tenant,project,script);
        if(!active(owner.get("execution_id")))return null;
        long id=((Number)owner.get("execution_id")).longValue();
        if(fingerprint.equals(owner.get("request_fingerprint")))return id;
        throw new AssetExtractionConflictException(id);
    }
    @Transactional
    public void attach(Long tenant,Long project,Long script,Long execution,String fingerprint) {
        var owner=lock(tenant,project,script);
        if(active(owner.get("execution_id")))throw new BusinessException(ErrorCode.VALIDATION_ERROR,"资产提取任务已被其他请求占用。");
        jdbc.update("""
            update script_asset_extraction_owner set execution_id=?,execution_version=null,attempt_id=null,
              request_fingerprint=?,updated_at=now() where tenant_id=? and project_id=? and script_id=?
            """,execution,fingerprint,tenant,project,script);
    }
    @Transactional
    public void acquire(Long script,AiExecutionContext context) {
        var task=context.task();
        var owner=lock(task.tenantId,task.projectId,script);
        Object existing=owner.get("execution_id");
        if(existing!=null && ((Number)existing).longValue()!=task.id && active(existing))
            throw new AiExecutionDeferredException("等待同剧本资产提取任务 "+existing);
        requireAttempt(task.id,task.executionVersion,context.claim().attemptId());
        jdbc.update("""
            update script_asset_extraction_owner set
              request_fingerprint=case when execution_id=? then request_fingerprint else null end,
              execution_id=?,execution_version=?,attempt_id=?,updated_at=now()
            where tenant_id=? and project_id=? and script_id=?
            """,task.id,task.id,task.executionVersion,context.claim().attemptId(),task.tenantId,task.projectId,script);
    }
    private void requireAttempt(Long execution,Integer version,Long attempt) {
        var rows=jdbc.queryForList("""
            select e.id from ai_execution_task e join ai_execution_attempt a
              on a.execution_id=e.id and a.execution_version=e.execution_version
            where e.id=? and e.execution_version=? and e.status='RUNNING'
              and e.claim_expires_at > now() and a.id=? and a.status='STARTED' for update
            """,Long.class,execution,version,attempt);
        if(rows.size()!=1)throw new AiExecutionClaimLostException(execution);
    }
    @Transactional
    public void requireOwned(Long tenant,Long project,Long script,Long execution,Integer version,Long attempt) {
        var owner=lock(tenant,project,script);
        if(!equal(owner.get("execution_id"),execution) || !equal(owner.get("execution_version"),version)
            || !equal(owner.get("attempt_id"),attempt))throw new AiExecutionClaimLostException(execution);
        requireAttempt(execution,version,attempt);
    }
    public void requireOwned(ToolExecutionContext c) {
        requireOwned(c.tenantId(),c.projectId(),c.scriptId(),c.executionId(),c.executionVersion(),c.attemptId());
    }
    private boolean equal(Object stored,Number expected) {
        return stored instanceof Number n && expected!=null && n.longValue()==expected.longValue();
    }
    @Transactional
    public void release(Long script,AiExecutionContext c) {
        jdbc.update("""
            update script_asset_extraction_owner set execution_id=null,execution_version=null,attempt_id=null,
              request_fingerprint=null,updated_at=now()
            where tenant_id=? and project_id=? and script_id=? and execution_id=? and execution_version=? and attempt_id=?
            """,c.task().tenantId,c.task().projectId,script,c.task().id,c.task().executionVersion,c.claim().attemptId());
    }
    public String fingerprint(Long tenant,Long project,Long script,Object user,Object version,Object model,Object request) {
        var episodes=jdbc.queryForList("""
            select id,stable_key,content_fingerprint from script_episode
            where tenant_id=? and project_id=? and script_id=? and status='ACTIVE' and retired_at is null order by id
            """,tenant,project,script);
        var revision=jdbc.queryForList("select revision from ai_workflow_agent where code='short-drama-asset-recognition'");
        return EpisodePromptContextFactory.hash(List.of(user,version,model,request,episodes,revision).toString());
    }
}
