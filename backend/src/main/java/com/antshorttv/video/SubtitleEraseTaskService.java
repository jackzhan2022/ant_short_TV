package com.antshorttv.video;

import com.antshorttv.common.*;
import com.antshorttv.execution.*;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.script.StoryboardMapper;
import com.antshorttv.security.*;
import com.antshorttv.querycache.QueryResultCache;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubtitleEraseTaskService {
    public static final String SCENE="video_subtitle_erase";
    private final SubtitleEraseTaskMapper tasks;
    private final AiVideoResultMapper results;
    private final AiVideoTaskMapper videos;
    private final StoryboardMapper storyboards;
    private final AiExecutionService executions;
    private final AiExecutionTaskMapper executionMapper;
    private final TenantContextResolver tenants;
    private final ProjectPermissionGuard permissions;
    private final MediaKitSubtitleClient client;
    private final ObjectMapper json;
    private QueryResultCache cache=QueryResultCache.disabled();
    @org.springframework.beans.factory.annotation.Autowired
    void setCache(QueryResultCache value) { cache=value; }

    public SubtitleEraseTaskService(SubtitleEraseTaskMapper tasks,AiVideoResultMapper results,AiVideoTaskMapper videos,
        StoryboardMapper storyboards,AiExecutionService executions,AiExecutionTaskMapper executionMapper,TenantContextResolver tenants,
        ProjectPermissionGuard permissions,MediaKitSubtitleClient client,ObjectMapper json) {
        this.tasks=tasks;this.results=results;this.videos=videos;this.storyboards=storyboards;this.executions=executions;this.executionMapper=executionMapper;
        this.tenants=tenants;this.permissions=permissions;this.client=client;this.json=json;
    }
    private TenantContext access(long tenant,long project,boolean write) {
        TenantContext context=tenants.requireActiveMember(tenant);
        permissions.require(tenant,project,"AI_VIDEO_TASK:VIEW");
        if (write) { permissions.require(tenant,project,"AI_VIDEO_TASK:CREATE"); permissions.require(tenant,project,"AI_SERVICE:USE"); }
        return context;
    }
    @Transactional
    public Response create(long tenant,long project,long resultId,SubtitleEraseRequest request,String requestKey) {
        TenantContext context=access(tenant,project,true);client.requireConfigured();request.validate();
        if(requestKey==null || requestKey.isBlank() || requestKey.length()>128) throw invalid("字幕擦除请求缺少有效的幂等标识。");
        String key=UUID.nameUUIDFromBytes((project+":"+resultId+":"+requestKey).getBytes(StandardCharsets.UTF_8)).toString();
        SubtitleEraseTaskEntity existing=tasks.selectOne(new QueryWrapper<SubtitleEraseTaskEntity>()
            .eq("tenant_id",tenant).eq("created_by",context.userId()).eq("client_key",key));
        String region=encode(request.region());
        if(existing!=null) {
            if(!Objects.equals(existing.method,request.method()) || !Objects.equals(existing.quality,request.quality())
                || !Objects.equals(existing.regionJson,region)) throw invalid("同一擦除请求不能修改处理参数，请重新发起。");
            return response(existing,context.userId());
        }
        // Serialize submissions for this exact source, across tabs and team members.
        AiVideoResultEntity source=results.selectOne(new QueryWrapper<AiVideoResultEntity>()
            .eq("tenant_id",tenant).eq("project_id",project).eq("id",resultId).eq("status","ACTIVE").last("for update"));
        requireUsableSource(source,tenant,project);
        SubtitleEraseTaskEntity active=tasks.selectOne(new QueryWrapper<SubtitleEraseTaskEntity>()
            .eq("tenant_id",tenant).eq("project_id",project).eq("source_result_id",resultId)
            .inSql("execution_id","select id from ai_execution_task where status in ('PENDING','RUNNING')")
            .orderByDesc("id").last("limit 1 for update"));
        if(active!=null) return response(active,context.userId());
        var task=new SubtitleEraseTaskEntity();
        task.tenantId=tenant;task.projectId=project;task.storyboardId=source.storyboardId;task.sourceResultId=resultId;
        task.method=request.method();task.quality=request.quality();task.regionJson=region;task.clientKey=key;
        task.generation=1;task.providerFailed=false;task.createdBy=context.userId();task.createdAt=LocalDateTime.now();task.updatedAt=task.createdAt;task.deadlineAt=task.createdAt.plusHours(6);
        tasks.insert(task);
        var execution=executions.create(new AiExecutionCreateCommand(tenant,context.userId(),project,SCENE,
            "VIDEO_SUBTITLE_ERASE","SUBTITLE_ERASE",task.id,null,"ERASE_SUBMIT",
            "subtitle-erase:"+context.userId()+":"+key,UUID.randomUUID().toString(),true,
            "{\"sourceResultId\":"+resultId+",\"method\":\""+task.method+"\",\"quality\":\""+task.quality+"\"}"));
        task.executionId=execution.id;tasks.updateById(task);invalidate(task);
        return response(task,context.userId());
    }
    public List<Response> list(long tenant,long project,List<Long> storyboardIds) {
        TenantContext context=access(tenant,project,false);
        if(storyboardIds==null || storyboardIds.isEmpty()) return List.of();
        if(storyboardIds.size()>100 || storyboardIds.stream().anyMatch(id->id==null || id<=0)) throw invalid("分镜查询范围不正确。");
        var rows=tasks.selectList(new QueryWrapper<SubtitleEraseTaskEntity>().eq("tenant_id",tenant).eq("project_id",project)
            .in("storyboard_id",storyboardIds).orderByDesc("id").last("limit 200"));
        if(rows.isEmpty()) return List.of();
        var executionRows=executionMapper.selectBatchIds(rows.stream().map(t->t.executionId).toList()).stream()
            .collect(java.util.stream.Collectors.toMap(e->e.id,e->e));
        var resultIds=rows.stream().map(t->t.resultId).filter(Objects::nonNull).distinct().toList();
        Map<Long,AiVideoResultEntity> resultRows=resultIds.isEmpty()?Map.of():results.selectBatchIds(resultIds).stream()
            .filter(r->"ACTIVE".equals(r.status)).collect(java.util.stream.Collectors.toMap(r->r.id,r->r));
        return rows.stream().filter(t->executionRows.containsKey(t.executionId))
            .map(t->response(t,context.userId(),executionRows.get(t.executionId),t.resultId==null?null:resultRows.get(t.resultId))).toList();
    }
    public Response detail(long tenant,long project,long id) {
        TenantContext context=access(tenant,project,false);return response(require(tenant,project,id),context.userId());
    }
    @Transactional
    public Response retry(long tenant,long project,long id,String requestKey) {
        TenantContext context=access(tenant,project,true);client.requireConfigured();
        SubtitleEraseTaskEntity task=require(tenant,project,id);
        if(!context.userId().equals(task.createdBy)) throw new BusinessException(ErrorCode.FORBIDDEN,"只能重试自己发起的字幕擦除任务。");
        var execution=executions.requireTaskForUpdate(task.executionId);
        task=tasks.selectOne(new QueryWrapper<SubtitleEraseTaskEntity>().eq("id",id).last("for update"));
        if(requestKey==null || requestKey.isBlank() || requestKey.length()>128) throw invalid("重试请求缺少有效的幂等标识。");
        if(Objects.equals(task.retryKey,requestKey)) return response(task,context.userId());
        if(List.of("PENDING","RUNNING","SUCCEEDED").contains(execution.status)) return response(task,context.userId());
        if(!List.of("FAILED","TIMED_OUT").contains(execution.status)) throw invalid("当前任务不能重试。");
        requireUsableSource(results.selectActive(tenant,project,task.sourceResultId),tenant,project);
        if(Boolean.TRUE.equals(task.providerFailed)) {
            tasks.update(null,new UpdateWrapper<SubtitleEraseTaskEntity>().set("provider_task_id",null).set("provider_failed",false)
                .set("input_url",null).set("output_url",null).set("output_expires_at",null)
                .set("generation",task.generation+1).set("updated_at",LocalDateTime.now()).eq("id",id));
        }
        tasks.update(null,new UpdateWrapper<SubtitleEraseTaskEntity>().set("deadline_at",LocalDateTime.now().plusHours(6))
            .set("retry_key",requestKey).set("updated_at",LocalDateTime.now()).eq("id",id));
        executions.retry(task.executionId);invalidate(task);
        return response(tasks.selectById(id),context.userId());
    }
    SubtitleEraseTaskEntity require(long tenant,long project,long id) {
        var task=tasks.selectOne(new QueryWrapper<SubtitleEraseTaskEntity>().eq("tenant_id",tenant).eq("project_id",project).eq("id",id));
        if(task==null) throw new BusinessException(ErrorCode.NOT_FOUND,"字幕擦除任务不存在。");return task;
    }
    void requireUsableSource(AiVideoResultEntity source,long tenant,long project) {
        if(source==null || !"ACTIVE".equals(source.status) || !Objects.equals(source.tenantId,tenant) || !Objects.equals(source.projectId,project)
            || storyboards.selectActive(tenant,project,source.storyboardId)==null)
            throw new BusinessException(ErrorCode.NOT_FOUND,"源视频或分镜已不存在。");
        var task=videos.selectById(source.taskId);
        if(task==null || task.deletedAt!=null || !Objects.equals(task.tenantId,tenant) || !Objects.equals(task.projectId,project))
            throw new BusinessException(ErrorCode.NOT_FOUND,"源视频生成记录已不存在。");
    }
    public Response response(SubtitleEraseTaskEntity task,long viewer) {
        var execution=executions.requireTask(task.executionId);
        var result=task.resultId==null?null:results.selectActive(task.tenantId,task.projectId,task.resultId);
        return response(task,viewer,execution,result);
    }
    private Response response(SubtitleEraseTaskEntity task,long viewer,AiExecutionTaskEntity execution,AiVideoResultEntity result) {
        String status="PENDING".equals(execution.status) && task.providerTaskId!=null?"RUNNING":execution.status;
        return new Response(task.id,task.executionId,task.storyboardId,task.sourceResultId,task.method,task.quality,status,
            execution.progress,execution.phase,List.of("FAILED","TIMED_OUT").contains(status)?execution.errorMessage:null,
            Objects.equals(task.createdBy,viewer)&&List.of("FAILED","TIMED_OUT").contains(status),task.createdAt,
            result==null?null:AiVideoResultResponse.fromBrowser(result));
    }
    void invalidate(SubtitleEraseTaskEntity task) {
        cache.invalidateAfterCommit(QueryResultCache.Scope.tenant(task.tenantId),QueryResultCache.Region.TASKS);
        cache.invalidateProjectAfterCommit(task.tenantId,task.projectId,QueryResultCache.Region.ANALYSIS);
    }
    private String encode(Object value) {
        if(value==null)return null;
        try{return json.writeValueAsString(value);}catch(Exception exception){throw invalid("擦除区域格式不正确。");}
    }
    private BusinessException invalid(String message){return new BusinessException(ErrorCode.VALIDATION_ERROR,message);}
    public record Response(Long id,Long executionId,Long storyboardId,Long sourceResultId,String method,String quality,
        String status,Integer progress,String phase,String errorMessage,boolean retryable,LocalDateTime createdAt,AiVideoResultResponse result) {}
}
