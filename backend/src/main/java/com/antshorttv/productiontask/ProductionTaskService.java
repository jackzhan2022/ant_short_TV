package com.antshorttv.productiontask;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProductionTaskService {
    private static final Map<String,String> OPERATION_PERMISSIONS=Map.of(
        "SCRIPT_GENERATE","SCRIPT:AI_GENERATE", "SCRIPT_REWRITE","SCRIPT:AI_REWRITE",
        "ELEMENT_EXTRACT","ELEMENT:AI_EXTRACT", "SCOPED_ASSET_REEXTRACTION","ELEMENT:AI_EXTRACT",
        "STORYBOARD_BREAKDOWN","STORYBOARD:AI_BREAKDOWN", "PROMPT_GENERATE","PROMPT:AI_GENERATE");
    private static final Map<String,String> OPERATION_NAMES=Map.of(
        "SCRIPT_GENERATE","剧本生成", "SCRIPT_REWRITE","剧本改写", "ELEMENT_EXTRACT","资产提取",
        "SCOPED_ASSET_REEXTRACTION","范围资产重提取", "STORYBOARD_BREAKDOWN","分镜生成", "PROMPT_GENERATE","提示词生成");
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate sql;
    private final TenantContextResolver tenants;
    private final RbacPermissionService permissions;
    public ProductionTaskService(JdbcTemplate jdbc, TenantContextResolver tenants, RbacPermissionService permissions) {
        this.jdbc=jdbc; this.sql=new NamedParameterJdbcTemplate(jdbc); this.tenants=tenants; this.permissions=permissions;
    }

    public record Page(List<Map<String,Object>> items, long total, int page, int pageSize, boolean canViewTeamTasks) {}
    public record Summary(long total, Map<String,Long> counts) {}
    record Access(TenantContext context, boolean team, Set<String> permissions) {}
    record Predicate(String text, Map<String,Object> params) {}

    Access access(long tenantId) {
        TenantContext c=tenants.requireActiveMember(tenantId);
        boolean team="OWNER".equals(c.memberType()) || jdbc.queryForObject("""
            select count(*) from member_role mr join `role` r on r.id=mr.role_id
            where mr.member_id=? and r.tenant_id=? and r.code='ADMIN' and r.role_type='SYSTEM'
              and r.status='ACTIVE' and r.deleted_at is null
            """,Long.class,c.memberId(),tenantId)>0;
        return new Access(c,team,permissions.permissionCodes(c));
    }

    private Predicate predicate(Access a,ProductionTaskQuery q) {
        Map<String,Object> p=new HashMap<>(); p.put("tenant",a.context.tenantId());
        StringBuilder where=new StringBuilder(" t.tenant_id=:tenant");
        if (q.effectiveScope().equals("team")) { if(!a.team) throw denied(); }
        else {
            if(q.creatorId()!=null && !q.creatorId().equals(a.context.userId())) throw denied();
            where.append(" and t.created_by=:user"); p.put("user",a.context.userId());
        }
        if(q.type()!=null && !q.type().isBlank()) {
            if(!ProductionTaskSources.TYPES.contains(q.type())) throw invalid();
            where.append(" and t.type=:type");p.put("type",q.type());
        }
        if(q.statusGroup()!=null && !q.statusGroup().isBlank()) {
            if(!List.of("QUEUED","RUNNING","WAITING_USER","SUCCEEDED","PARTIAL","FAILED","CANCELED").contains(q.statusGroup())) throw invalid();
            where.append(" and t.status_group=:state");p.put("state",q.statusGroup());
        }
        if(q.projectId()!=null) {where.append(" and t.project_id=:project");p.put("project",q.projectId());}
        if(q.creatorId()!=null) {where.append(" and t.created_by=:creator");p.put("creator",q.creatorId());}
        if(q.createdFrom()!=null) {where.append(" and t.created_at>=:since");p.put("since",q.createdFrom());}
        if(q.createdTo()!=null) {where.append(" and t.created_at<=:until");p.put("until",q.createdTo());}
        if(q.createdFrom()!=null && q.createdTo()!=null && q.createdFrom().isAfter(q.createdTo())) throw invalid();
        return new Predicate(where.toString(),p);
    }

    public Page list(long tenantId,ProductionTaskQuery query) {
        Access a=access(tenantId); Predicate p=predicate(a,query);
        return page(a,query,p.text+" and t.parent_id is null and t.root_visible=true",p.params);
    }

    private Page page(Access a,ProductionTaskQuery q,String where,Map<String,Object> params) {
        String from=" from ("+ProductionTaskSources.ALL+") t where "+where;
        long total=sql.queryForObject("select count(*)"+from,params,Long.class);
        params.put("limit",q.effectiveSize());params.put("offset",(long)(q.effectivePage()-1)*q.effectiveSize());
        List<Map<String,Object>> rows=sql.queryForList("select t.*"+from
            +" order by case when t.status_group in ('RUNNING','QUEUED') then 0 else 1 end, t.created_at desc,t.type,t.id desc limit :limit offset :offset",params);
        return new Page(present(a,rows),total,q.effectivePage(),q.effectiveSize(),a.team);
    }

    public Summary summary(long tenantId,ProductionTaskQuery query) {
        Access a=access(tenantId);Predicate p=predicate(a,query);
        Map<String,Long> counts=new LinkedHashMap<>();
        for(String status:List.of("QUEUED","RUNNING","WAITING_USER","SUCCEEDED","PARTIAL","FAILED","CANCELED")) counts.put(status,0L);
        sql.query("select t.status_group,count(*) amount from ("+ProductionTaskSources.ALL+") t where "+p.text
            +" and t.parent_id is null and t.root_visible=true group by t.status_group",p.params,rs->{counts.put(rs.getString(1),rs.getLong(2));});
        return new Summary(counts.values().stream().mapToLong(Long::longValue).sum(),counts);
    }

    Map<String,Object> requireRow(Access a,String key) {
        String[] parts=key.split(":",-1);
        if(parts.length!=2 || !ProductionTaskSources.TYPES.contains(parts[0]) || !parts[1].matches("[1-9][0-9]{0,17}")) throw invalid();
        List<Map<String,Object>> rows=sql.queryForList("select t.* from ("+ProductionTaskSources.ALL+") t where t.tenant_id=:tenant and t.type=:type and t.id=:id",
            Map.of("tenant",a.context.tenantId(),"type",parts[0],"id",Long.parseLong(parts[1])));
        if(rows.size()!=1) throw denied();
        Map<String,Object> row=rows.get(0);
        if(!a.team && !Objects.equals(number(row,"created_by"),a.context.userId())) throw denied();
        return row;
    }

    public Map<String,Object> detail(long tenantId,String key) {
        Access a=access(tenantId);return present(a,List.of(requireRow(a,key))).get(0);
    }

    public Page children(long tenantId,String key,ProductionTaskQuery query) {
        Access a=access(tenantId); Map<String,Object> parent=requireRow(a,key);
        Predicate p=predicate(a,query);
        p.params.put("parentType",parent.get("type"));p.params.put("parent",parent.get("id"));
        return page(a,query,p.text+" and t.parent_type=:parentType and t.parent_id=:parent",p.params);
    }

    private List<Map<String,Object>> present(Access a,List<Map<String,Object>> rows) {
        if(rows.isEmpty()) return List.of();
        Set<Long> projectIds=new HashSet<>(),userIds=new HashSet<>();
        rows.forEach(r->{if(number(r,"project_id")!=null) projectIds.add(number(r,"project_id"));userIds.add(number(r,"created_by"));});
        Map<Long,String> projectNames=new HashMap<>(),userNames=new HashMap<>();
        Map<Long,Set<String>> projectPermissions=new HashMap<>();
        Map<String,Object> params=new HashMap<>();params.put("tenant",a.context.tenantId());params.put("user",a.context.userId());
        if(!projectIds.isEmpty()) {
            params.put("projects",projectIds);
            sql.query("select id,name from project where tenant_id=:tenant and id in (:projects) and deleted_at is null",params,
                rs->{projectNames.put(rs.getLong(1),rs.getString(2));});
            if(a.permissions.contains("PROJECT:VIEW_ALL")) projectNames.keySet().forEach(id->projectPermissions.put(id,a.permissions));
            else sql.query("""
                select pm.project_id,p.code from project_member pm
                join project_role pr on pr.id=pm.role_id and pr.tenant_id=pm.tenant_id and pr.project_id=pm.project_id and pr.status='ACTIVE'
                join project_role_permission rp on rp.role_id=pr.id and rp.tenant_id=pr.tenant_id and rp.project_id=pr.project_id
                join permission p on p.id=rp.permission_id
                where pm.tenant_id=:tenant and pm.user_id=:user and pm.status='ACTIVE' and pm.project_id in (:projects)
                """,params,rs->{projectPermissions.computeIfAbsent(rs.getLong(1),id->new HashSet<>()).add(rs.getString(2));});
        }
        params.put("users",userIds);
        sql.query("""
            select u.id,u.nickname,m.status from app_user u
            left join tenant_member m on m.user_id=u.id and m.tenant_id=:tenant where u.id in (:users)
            """,params,rs->{userNames.put(rs.getLong(1),rs.getString(2)+("ACTIVE".equals(rs.getString(3))?"":"（已离开团队）"));});
        List<Map<String,Object>> result=new ArrayList<>();
        Set<String> warnings=warningKeys(a,rows);
        Set<String> mediaDestinations=mediaDestinations(a,rows);
        for(Map<String,Object> row:rows) {
            Long project=number(row,"project_id"),creator=number(row,"created_by");
            Set<String> effective=project==null?a.permissions:projectPermissions.getOrDefault(project,Set.of());
            boolean view=project==null || (projectNames.containsKey(project) && (effective.contains("PROJECT:VIEW")||effective.contains("PROJECT:VIEW_ALL")));
            String type=(String)row.get("type"), state=(String)row.get("status_group");
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("taskKey",type+":"+number(row,"id"));item.put("type",type);
            item.put("title",view?row.get("title"):"项目任务（访问受限）");
            if(view && type.equals("SCRIPT_OPERATION")) item.put("title",OPERATION_NAMES.getOrDefault(String.valueOf(row.get("subtype")),"剧本处理")+" #"+number(row,"id"));
            item.put("projectId",view?project:null);item.put("projectName",view?projectNames.get(project):null);
            item.put("creatorId",creator);item.put("creatorName",userNames.getOrDefault(creator,"已离开团队的成员"));
            item.put("statusGroup",state);item.put("domainStatus",row.get("domain_status"));
            item.put("progress",view?row.get("progress"):null);item.put("phase",view?row.get("phase"):null);
            item.put("createdAt",row.get("created_at"));item.put("completedAt",row.get("completed_at"));
            item.put("restricted",!view);
            Map<String,Long> counts=new LinkedHashMap<>();
            for(String c:List.of("total","success","failed","canceled","running","queued","waiting")) counts.put(c,view?number(row,"child_"+c):0L);
            item.put("childCounts",counts);
            item.put("rootExecutionId",view?number(row,"root_execution_id"):null);
            item.put("sourceExecutionId",view?number(row,"source_execution_id"):null);
            String destination=view?destination(type,number(row,"id"),project,(String)row.get("subtype")):null;
            if(type.equals("IMAGE") && (!effective.contains("AI_IMAGE_TASK:VIEW") || !mediaDestinations.contains((String)item.get("taskKey")))) destination=null;
            if(type.equals("VIDEO") && (!effective.contains("AI_VIDEO_TASK:VIEW") || !mediaDestinations.contains((String)item.get("taskKey")))) destination=null;
            if(List.of("STORYBOARD_BATCH","STORYBOARD_ITEM").contains(type) && !effective.contains("STORYBOARD:VIEW")) destination=null;
            boolean ownReviewProject=String.valueOf(a.context.userId()).equals(row.get("subtype"));
            if(project==null && "REVIEW".equals(type) && !ownReviewProject && !a.permissions.contains("PROJECT:VIEW_ALL")) destination=null;
            item.put("destination",destination);
            item.put("resultSummary",view && "SUCCEEDED".equals(state)?(destination==null?"已完成，当前结果入口不可用":"已完成，可返回业务查看结果"):null);
            boolean warning=String.valueOf(row.get("domain_status")).contains("WARNING") || warnings.contains("E:"+number(row,"execution_id")) || (type.equals("STORYBOARD_BATCH") && warnings.contains("B:"+number(row,"id")));
            item.put("warningSummary",view && warning?"已生成内容含警告，请返回业务查看说明。":null);
            if(view && !ProductionTaskSources.KNOWN_STATES.contains(String.valueOf(row.get("domain_status")))) item.put("warningSummary","历史任务状态无法识别，已归入异常任务，请返回业务核实。");
            List<String> actions=new ArrayList<>();if(destination!=null) actions.add("OPEN");
            if(view && creator.equals(a.context.userId())) {
                boolean active=List.of("QUEUED","RUNNING").contains(state);
                if(type.equals("IMAGE") && active && number(row,"execution_id")!=null && effective.contains("AI_IMAGE_TASK:CANCEL")) actions.add("CANCEL");
                if(type.equals("VIDEO") && active && effective.contains("AI_VIDEO_TASK:CANCEL")) actions.add("CANCEL");
                if(type.equals("IMAGE") && state.equals("SUCCEEDED") && number(row,"execution_id")!=null
                    && !"VISUAL_VARIANT".equals(row.get("subtype"))
                    && effective.containsAll(Set.of("AI_IMAGE_TASK:CREATE","AI_SERVICE:USE"))) actions.add("REGENERATE");
                String operationPermission=type.equals("STORYBOARD_ITEM")?"STORYBOARD:AI_BREAKDOWN":OPERATION_PERMISSIONS.get(String.valueOf(row.get("subtype")));
                if(List.of("SCRIPT_OPERATION","STORYBOARD_ITEM").contains(type) && Objects.equals(number(row,"execution_user"),creator)
                    && effective.contains("AI_SERVICE:USE") && operationPermission!=null && effective.contains(operationPermission)) {
                    if(active) actions.add("CANCEL");
                    if(List.of("FAILED","TIMED_OUT").contains(String.valueOf(row.get("domain_status"))) && truth(row.get("retryable"))) actions.add("RETRY");
                }
                if(type.equals("VIDEO_EPISODE") && state.equals("FAILED") && number(row,"execution_id")!=null && truth(row.get("retryable"))
                    && (project==null || effective.contains("AI_SERVICE:USE"))) actions.add("RETRY");
                if(type.equals("REVIEW") && (project==null ? ownReviewProject || effective.contains("PROJECT:EDIT_ALL") : effective.contains("AI_SERVICE:USE"))) {
                    if(active && number(row,"execution_id")!=null) actions.add("CANCEL");
                    if("FAILED".equals(row.get("domain_status")) && number(row,"execution_id")!=null && truth(row.get("retryable"))) actions.add("RETRY");
                }
            }
            item.put("allowedActions",actions);result.add(item);
        }
        return result;
    }

    private Set<String> warningKeys(Access a,List<Map<String,Object>> rows) {
        List<Long> executions=rows.stream().filter(r->"SUCCEEDED".equals(r.get("status_group"))).map(r->number(r,"execution_id")).filter(Objects::nonNull).toList();
        List<Long> batches=rows.stream().filter(r->"STORYBOARD_BATCH".equals(r.get("type"))).map(r->number(r,"id")).toList();
        if(executions.isEmpty() && batches.isEmpty()) return Set.of();
        Set<String> keys=new HashSet<>();
        sql.query("""
            select distinct cl.execution_id, bi.batch_id from storyboard s
            join ai_workflow_agent_run_step step on step.run_id=s.generated_by_run_id
            join ai_call_log cl on cl.id=step.ai_call_log_id and cl.tenant_id=s.tenant_id
            left join storyboard_batch_item bi on bi.execution_id=cl.execution_id and bi.tenant_id=s.tenant_id
            where s.tenant_id=:tenant and s.deleted_at is null
              and (cl.execution_id in (:executions) or bi.batch_id in (:batches))
              and regexp_like(s.shot_plan_json,:pattern)
            """,Map.of("tenant",a.context.tenantId(),"executions",executions.isEmpty()?List.of(-1L):executions,
                "batches",batches.isEmpty()?List.of(-1L):batches,
                "pattern","\"(warnings|classificationWarnings)\"\\s*:\\s*\\[\\s*[^\\s\\]]"),rs->{
                    keys.add("E:"+rs.getLong(1)); if(rs.getObject(2)!=null) keys.add("B:"+rs.getLong(2));
                });
        return keys;
    }

    private Set<String> mediaDestinations(Access a,List<Map<String,Object>> rows) {
        Set<String> available=new HashSet<>();
        List<Long> images=rows.stream().filter(r->"IMAGE".equals(r.get("type"))).map(r->number(r,"id")).toList();
        List<Long> videos=rows.stream().filter(r->"VIDEO".equals(r.get("type"))).map(r->number(r,"id")).toList();
        if(!images.isEmpty()) {
            List<String> targets=new ArrayList<>();
            Map.of("CHARACTER","character_asset","SCENE","scene_asset","PROP","prop_asset","STORYBOARD","storyboard","VISUAL_VARIANT","asset_visual_variant")
                .forEach((type,table)->targets.add("(t.target_type='"+type+"' and exists (select 1 from "+table+" a where a.id=t.target_id and a.tenant_id=t.tenant_id and a.project_id=t.project_id and a.deleted_at is null))"));
            sql.query("select t.id from ai_image_task t where t.tenant_id=:tenant and t.id in (:ids) and ("+String.join(" or ",targets)+")",
                Map.of("tenant",a.context.tenantId(),"ids",images),rs->{available.add("IMAGE:"+rs.getLong(1));});
        }
        if(!videos.isEmpty()) sql.query("""
            select t.id from ai_video_task t join storyboard s on s.id=t.storyboard_id and s.tenant_id=t.tenant_id and s.project_id=t.project_id
            where t.tenant_id=:tenant and t.id in (:ids) and s.deleted_at is null
            """,Map.of("tenant",a.context.tenantId(),"ids",videos),rs->{available.add("VIDEO:"+rs.getLong(1));});
        return available;
    }

    private static String destination(String type,Long id,Long project,String subtype) {
        if(type.equals("REVIEW")) return "/script-review/tasks/"+id;
        if(type.startsWith("VIDEO_DECOMPOSITION")||type.equals("VIDEO_EPISODE")) return "/video-script-decomposition";
        if(project==null) return null;
        String section=List.of("SCRIPT_ANALYSIS","SCRIPT_OPERATION").contains(type)?"script":"storyboard";
        if(type.equals("VIDEO")) section="video";
        if(type.equals("IMAGE") && !"STORYBOARD".equals(subtype)) section="settings";
        if(type.equals("SCRIPT_OPERATION") && "STORYBOARD_BREAKDOWN".equals(subtype)) section="storyboard";
        return "/projects/"+project+"/production-workbench/"+section;
    }
    static Long number(Map<String,Object> row,String key) {Object value=row.get(key);return value instanceof Number n?n.longValue():null;}
    static boolean truth(Object value) {return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue()!=0;}
    static BusinessException denied() {return new BusinessException(ErrorCode.FORBIDDEN,"无权访问或操作该生产任务。");}
    static BusinessException invalid() {return new BusinessException(ErrorCode.VALIDATION_ERROR,"任务查询参数无效。");}
}
