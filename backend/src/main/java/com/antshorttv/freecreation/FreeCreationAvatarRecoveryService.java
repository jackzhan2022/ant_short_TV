package com.antshorttv.freecreation;

import com.antshorttv.ai.VolcengineProviderCodes;
import com.antshorttv.authsession.AuthenticatedUser;
import com.antshorttv.avatar.ArkAssetPool;
import com.antshorttv.common.*;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.video.SeedanceRealPersonRejection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Free creation binding for the shared Seedance ingestion and recovery queue. */
@Service
public class FreeCreationAvatarRecoveryService {
    public static final int HARD_MAX_ATTEMPTS=5;
    public static final int DEFAULT_MAX_ATTEMPTS=3;
    private final ObjectMapper snapshots=new ObjectMapper();
    private int maxAttempts=DEFAULT_MAX_ATTEMPTS;
    @Value("${ark.avatar.free-creation-max-attempts:3}")
    public void setMaxAttempts(int value) {maxAttempts=Math.max(1,Math.min(HARD_MAX_ATTEMPTS,value));}
    public int maxAttempts() {return maxAttempts;}
    private final JdbcTemplate jdbc;
    private final FreeCreationMapper creations;
    private final AiExecutionService executions;
    private final ObjectProvider<FreeCreationService> service;
    private final RbacPermissionService permissions;
    private final boolean enabled;
    public FreeCreationAvatarRecoveryService(JdbcTemplate jdbc,FreeCreationMapper creations,AiExecutionService executions,
        ObjectProvider<FreeCreationService> service,RbacPermissionService permissions,
        @Value("${ark.avatar.recovery-enabled:true}") boolean enabled) {
        this.jdbc=jdbc;this.creations=creations;this.executions=executions;this.service=service;
        this.permissions=permissions;this.enabled=enabled;
    }
    public void enqueue(FreeCreationEntity source,String error) {
        if(!enabled || !"VIDEO".equals(source.mediaType)
            || !VolcengineProviderCodes.isArk(source.providerCode) || !SeedanceRealPersonRejection.matches(error)
            || source.videoRequestSnapshotJson==null) return;
        int attempt=nextAttempt(source);
        if(attempt<1 || attempt>maxAttempts || !canPrepareNewImage(source.videoRequestSnapshotJson,error))return;
        try {
            jdbc.update("insert into seedance_avatar_recovery(source_creation_id,tenant_id,created_by,prepared_snapshot_json,source_error_message,next_poll_at,deadline_at) values(?,?,?,?,?,now(),?)",
                source.id,source.tenantId,source.creatorId,source.videoRequestSnapshotJson,safe(error,2000),LocalDateTime.now().plusDays(1));
        } catch(DuplicateKeyException duplicate) { /* At most one automatic child per failed attempt. */ }
    }
    public FreeCreationEntity requireSource(Map<String,Object> job) {
        var source=creations.selectById(((Number)job.get("source_creation_id")).longValue());
        if(source==null || !source.tenantId.equals(((Number)job.get("tenant_id")).longValue())
            || !source.creatorId.equals(((Number)job.get("created_by")).longValue())
            || !"VIDEO".equals(source.mediaType)
            || !VolcengineProviderCodes.isArk(source.providerCode)) throw new IllegalStateException("原自由创作视频不存在或不支持自动恢复。");
        requireRecoveryBudget(source);
        String status=executions.requireTask(source.executionId).status;
        if(Set.of("PENDING","RUNNING").contains(status)) throw new ArkAssetPool.Waiting("正在确认原视频任务结果，请稍候。");
        if(!"FAILED".equals(status)) throw new IllegalStateException("原视频状态已变化，自动恢复已停止。");
        return source;
    }
    public void requireRecoveryBudget(FreeCreationEntity source) {
        int attempt=nextAttempt(source);
        if(attempt<1)throw new IllegalStateException("原任务关联链不可用，自动恢复已停止。");
        if(attempt>maxAttempts)throw new BusinessException(ErrorCode.VALIDATION_ERROR,"已达到自动恢复上限 "+maxAttempts+" 轮。");
    }
    private int nextAttempt(FreeCreationEntity source) {
        if(source==null || source.id==null || source.tenantId==null || source.creatorId==null)return 0;
        var seen=new HashSet<Long>();var current=source;int attempt=1;
        while(true) {
            if(!seen.add(current.id) || !"VIDEO".equals(current.mediaType) || !VolcengineProviderCodes.isArk(current.providerCode))return 0;
            if(current.avatarRecoverySourceCreationId==null)return attempt;
            if(attempt>HARD_MAX_ATTEMPTS)return 0;
            Long parentId=current.avatarRecoverySourceCreationId;var parent=creations.selectById(parentId);
            if(parent==null || !parentId.equals(parent.id) || !source.tenantId.equals(parent.tenantId) || !source.creatorId.equals(parent.creatorId))return 0;
            current=parent;attempt++;
        }
    }
    private boolean canPrepareNewImage(String snapshot,String error) {
        try {
            Set<Integer> rejected=SeedanceRealPersonRejection.rejectedContentIndices(error);int index=0;
            for(JsonNode item:snapshots.readTree(snapshot).path("references")) {
                index++;var reference=item.path("reference");
                if(!"IMAGE".equalsIgnoreCase(reference.path("mediaType").asText()) || item.path("avatarPrepared").asBoolean())continue;
                String type=reference.path("assetType").asText("");
                if(rejected.isEmpty()?(type.isBlank() || "CHARACTER".equalsIgnoreCase(type)):rejected.contains(index))return true;
            }
        } catch(Exception invalidSnapshot) {return false;}
        return false;
    }
    @Transactional
    public Long createRetry(long id,String token,JsonNode snapshot) {
        var job=jdbc.queryForMap("select * from seedance_avatar_recovery where id=? for update",id);
        if("SUCCEEDED".equals(job.get("status"))) return ((Number)job.get("retry_creation_id")).longValue();
        if(!"RUNNING".equals(job.get("status")) || !token.equals(job.get("claim_token"))
            || job.get("claim_until")==null || !((Timestamp)job.get("claim_until")).toLocalDateTime().isAfter(LocalDateTime.now()))
            throw new ArkAssetPool.Waiting("恢复任务执行租约已失效。");
        var source=requireSource(job);
        var previous=SecurityContextHolder.getContext();
        var security=SecurityContextHolder.createEmptyContext();
        security.setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(source.creatorId,"",
            "free-avatar-recovery:"+id,LocalDateTime.now().plusMinutes(1)),null,List.of()));
        SecurityContextHolder.setContext(security);
        try {
            var context=service.getObject().authorize(source.tenantId);
            if(!source.creatorId.equals(context.userId()) || !permissions.hasPermission(context,"MENU:FREE_CREATION"))
                throw new BusinessException(ErrorCode.FORBIDDEN,"原任务发起人已无自由创作权限。");
            Long retry=service.getObject().createAvatarRecovery(source.tenantId,source.id,"avatar-recovery-"+id,snapshot);
            jdbc.update("update seedance_avatar_recovery set status='SUCCEEDED',phase='已创建关联重试视频',retry_creation_id=?,error_message=null,updated_at=now() where id=? and claim_token=?",retry,id,token);
            return retry;
        } finally { SecurityContextHolder.setContext(previous); }
    }
    public Map<String,Object> get(long tenant,long source) {
        var rows=jdbc.queryForList("select r.*,e.status as retry_status from seedance_avatar_recovery r left join free_creation f on f.id=r.retry_creation_id and f.tenant_id=r.tenant_id left join ai_execution_task e on e.id=f.execution_id where r.tenant_id=? and r.source_creation_id=?",tenant,source);
        if(rows.isEmpty()) return null;
        var row=rows.get(0);var result=new LinkedHashMap<String,Object>();
        for(String key:List.of("id","status","phase"))result.put(key,row.get(key));
        result.put("retryCreationId",row.get("retry_creation_id"));result.put("errorMessage",row.get("error_message"));
        String status=(String)row.get("retry_status");
        var sourceCreation=creations.selectById(source);
        result.put("attempt",Math.max(1,nextAttempt(sourceCreation)));result.put("maxAttempts",maxAttempts);
        result.put("retryStatus",status==null?null:"PENDING".equals(status)?"QUEUED":"TIMED_OUT".equals(status)?"FAILED":status);
        return result;
    }
    public Long retryTarget(long tenant,long source) {
        var recovery=get(tenant,source);if(recovery==null)return null;
        if(Set.of("PENDING","RUNNING").contains(recovery.get("status")))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,"虚拟人像素材正在自动准备，完成后将继续生成，请勿重复提交。");
        return "SUCCEEDED".equals(recovery.get("status")) && recovery.get("retryCreationId") instanceof Number id?id.longValue():null;
    }
    private String safe(String value,int limit) {
        String safe=value.replaceAll("([?&](?:token|signature|api_key|access_key|secret_key)=)[^&\\s]+","$1***");
        return safe.substring(0,Math.min(limit,safe.length()));
    }
}
