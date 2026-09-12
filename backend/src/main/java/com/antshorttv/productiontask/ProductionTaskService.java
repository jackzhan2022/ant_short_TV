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

    /** Bounded, task-scoped read model. It never calls production services or writes state. */
    public Map<String,Object> content(long tenantId,String key) {
        Access a=access(tenantId); Map<String,Object> row=requireRow(a,key);
        Map<String,Object> summary=present(a,List.of(row)).get(0);
        List<Map<String,Object>> sections;
        if(Boolean.TRUE.equals(summary.get("restricted")) || !canReadContent(a,row)) {
            sections=List.of(section("submission","本次提交","FIELDS","RESTRICTED",null,List.of(),List.of(),false));
        } else {
            sections=sections(a,row);
        }
        return Map.of("schemaVersion",1,"taskKey",key,"contentRevision",contentRevision(a.context.tenantId(), row),"sections",sections);
    }

    /** Reads an allowlisted text section in bounded chunks; it never accepts storage or SQL identifiers. */
    public Map<String,Object> contentSection(long tenantId,String key,String sectionKey,int offset) {
        Access a=access(tenantId); Map<String,Object> row=requireRow(a,key);
        Map<String,Object> summary=present(a,List.of(row)).get(0);
        if(Boolean.TRUE.equals(summary.get("restricted")) || !canReadContent(a,row)) throw denied();
        String body=sectionText(a.context.tenantId(),row,sectionKey);
        if(body==null || offset<0 || offset>body.length()) throw invalid();
        int end=Math.min(body.length(),offset+32000);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("taskKey",key); result.put("sectionKey",sectionKey); result.put("availability",available(body));
        result.put("text",body.substring(offset,end)); result.put("hasMore",end<body.length());
        if(end<body.length()) result.put("nextOffset",end);
        return result;
    }

    private String sectionText(long tenant,Map<String,Object> row,String sectionKey) {
        long id=Objects.requireNonNull(number(row,"id"));
        return switch(String.valueOf(row.get("type"))) {
            case "IMAGE" -> sectionKey.equals("submission") ? text(one("select prompt from ai_image_task where tenant_id=? and id=?",tenant,id).get("prompt")) : null;
            case "VIDEO" -> sectionKey.equals("submission") ? text(one("select prompt from ai_video_task where tenant_id=? and id=?",tenant,id).get("prompt")) : null;
            case "SCRIPT_ANALYSIS" -> sectionKey.equals("submission") ? scriptVersionContent(tenant,"script_analysis_task",id) : null;
            case "REVIEW" -> switch(sectionKey) {
                case "submission" -> reviewVersionContent(tenant,id);
                case "results" -> text(one("select report_markdown from review_task where tenant_id=? and id=?",tenant,id).get("report_markdown"));
                default -> null;
            };
            case "VIDEO_EPISODE" -> sectionKey.equals("results") ? episodeResultContent(tenant,id) : null;
            case "SCRIPT_OPERATION" -> switch(sectionKey) {
                case "submission" -> scriptVersionContent(tenant,"script_ai_operation",id);
                case "results" -> operationResultContent(tenant,id);
                default -> null;
            };
            default -> null;
        };
    }

    private String scriptVersionContent(long tenant,String table,long id) {
        Map<String,Object> task=one("select script_version_id from "+table+" where tenant_id=? and id=?",tenant,id);
        return text(one("select content from script_version where tenant_id=? and id=?",tenant,number(task,"script_version_id")).get("content"));
    }
    private String reviewVersionContent(long tenant,long id) {
        Map<String,Object> task=one("select script_version_id from review_task where tenant_id=? and id=?",tenant,id);
        return text(one("select content from review_script_version where tenant_id=? and id=?",tenant,number(task,"script_version_id")).get("content"));
    }
    private String episodeResultContent(long tenant,long id) {
        String result=text(one("select content from video_decomposition_script_result where tenant_id=? and episode_id=?",tenant,id).get("content"));
        return first(result,text(one("select draft_content from video_decomposition_episode where tenant_id=? and id=?",tenant,id).get("draft_content")));
    }
    private String operationResultContent(long tenant,long id) {
        Map<String,Object> task=one("select result_type,result_id from script_ai_operation where tenant_id=? and id=?",tenant,id);
        return "SCRIPT_VERSION".equals(task.get("result_type")) ? text(one("select content from script_version where tenant_id=? and id=?",tenant,number(task,"result_id")).get("content")) : "";
    }

    private boolean canReadContent(Access a,Map<String,Object> row) {
        Long project=number(row,"project_id");
        if(project==null) return Objects.equals(number(row,"created_by"),a.context.userId());
        Set<String> codes=projectPermissions(a,project);
        String type=String.valueOf(row.get("type"));
        if(type.equals("IMAGE")) return codes.contains("AI_IMAGE_TASK:VIEW");
        if(type.equals("VIDEO")) return codes.contains("AI_VIDEO_TASK:VIEW");
        if(type.equals("SCRIPT_ANALYSIS")) return codes.contains("SCRIPT:VIEW");
        if(type.equals("STORYBOARD_BATCH") || type.equals("STORYBOARD_ITEM")) return codes.contains("STORYBOARD:VIEW");
        if(type.equals("SCRIPT_OPERATION")) return codes.contains(scriptOperationContentPermission(String.valueOf(row.get("subtype"))));
        return codes.contains("PROJECT:VIEW") || codes.contains("PROJECT:VIEW_ALL");
    }

    static String scriptOperationContentPermission(String operation) {
        return OPERATION_NAMES.containsKey(operation) ? "SCRIPT:VIEW" : "UNSUPPORTED";
    }

    private String contentRevision(long tenant, Map<String,Object> row) {
        String table=switch(String.valueOf(row.get("type"))) {
            case "IMAGE" -> "ai_image_task";
            case "VIDEO" -> "ai_video_task";
            case "SCRIPT_ANALYSIS" -> "script_analysis_task";
            case "SCRIPT_OPERATION" -> "script_ai_operation";
            case "REVIEW" -> "review_task";
            case "VIDEO_DECOMPOSITION" -> "video_decomposition_batch";
            case "VIDEO_EPISODE" -> "video_decomposition_episode";
            case "STORYBOARD_BATCH" -> "storyboard_batch";
            case "STORYBOARD_ITEM" -> "storyboard_batch_item";
            default -> null;
        };
        if(table==null) return "unknown";
        String column=table.startsWith("storyboard_batch") ? "created_at" : "updated_at";
        Map<String,Object> revision=one("select "+column+" revision from "+table+" where tenant_id=? and id=?",tenant,number(row,"id"));
        return text(revision.get("revision"));
    }

    private Set<String> projectPermissions(Access a,long project) {
        if(a.permissions.contains("PROJECT:VIEW_ALL")) return a.permissions;
        Set<String> codes=new HashSet<>();
        sql.query("""
            select p.code from project_member pm
            join project_role pr on pr.id=pm.role_id and pr.tenant_id=pm.tenant_id and pr.project_id=pm.project_id and pr.status='ACTIVE'
            join project_role_permission rp on rp.role_id=pr.id and rp.tenant_id=pr.tenant_id and rp.project_id=pr.project_id
            join permission p on p.id=rp.permission_id
            where pm.tenant_id=:tenant and pm.user_id=:user and pm.status='ACTIVE' and pm.project_id=:project
            """,Map.of("tenant",a.context.tenantId(),"user",a.context.userId(),"project",project),
            (org.springframework.jdbc.core.RowCallbackHandler) rs->codes.add(rs.getString(1)));
        return codes;
    }

    private List<Map<String,Object>> sections(Access a,Map<String,Object> row) {
        long tenant=a.context.tenantId(), id=Objects.requireNonNull(number(row,"id"));
        return switch(String.valueOf(row.get("type"))) {
            case "IMAGE" -> imageSections(tenant,id);
            case "VIDEO" -> videoSections(tenant,id);
            case "SCRIPT_ANALYSIS" -> analysisSections(tenant,id);
            case "REVIEW" -> reviewSections(tenant,id);
            case "VIDEO_DECOMPOSITION", "VIDEO_EPISODE" -> decompositionSections(tenant,id,String.valueOf(row.get("type")));
            case "STORYBOARD_BATCH", "STORYBOARD_ITEM" -> storyboardSections(tenant,id,String.valueOf(row.get("type")));
            case "SCRIPT_OPERATION" -> operationSections(tenant,id);
            default -> List.of(section("submission","本次提交","FIELDS","UNSUPPORTED",null,List.of(),List.of(),false));
        };
    }

    private List<Map<String,Object>> imageSections(long tenant,long id) {
        Map<String,Object> task=one("select prompt,negative_prompt,reference_images,model,provider_code,aspect_ratio,image_count,style,quality,seed,status,error_message from ai_image_task where tenant_id=? and id=?",tenant,id);
        String prompt=text(task.get("prompt"));
        List<Map<String,Object>> results=sql.queryForList("select id,image_url,thumbnail_url,width,height,file_size,is_selected,status from ai_image_result where tenant_id=:tenant and task_id=:task order by id",Map.of("tenant",tenant,"task",id));
        return List.of(section("submission","本次提交","TEXT",available(prompt),prompt,fields("负向提示词",task.get("negative_prompt"),"参考图",task.get("reference_images")),List.of(),more(prompt)),
            section("settings","生成设置","FIELDS","AVAILABLE",null,fields("模型",task.get("model"),"服务商",task.get("provider_code"),"比例",task.get("aspect_ratio"),"数量",task.get("image_count"),"风格",task.get("style"),"质量",task.get("quality"),"种子",task.get("seed")),List.of(),false),
            section("results","生成结果","IMAGE",results.isEmpty()?"PENDING":"AVAILABLE",null,List.of(),media(results,"image_url"),false));
    }

    private List<Map<String,Object>> videoSections(long tenant,long id) {
        Map<String,Object> task=one("select prompt,negative_prompt,first_frame_url,last_frame_url,reference_images,model,provider_code,duration_seconds,aspect_ratio,resolution,motion_strength,camera_movement,random_seed from ai_video_task where tenant_id=? and id=?",tenant,id);
        String prompt=text(task.get("prompt"));
        List<Map<String,Object>> results=sql.queryForList("select id,video_url,cover_url thumbnail_url,duration_seconds,width,height,file_size,format,is_selected,status from ai_video_result where tenant_id=:tenant and task_id=:task order by id",Map.of("tenant",tenant,"task",id));
        return List.of(section("submission","本次提交","TEXT",available(prompt),prompt,fields("负向提示词",task.get("negative_prompt"),"首帧",task.get("first_frame_url"),"尾帧",task.get("last_frame_url"),"参考素材",task.get("reference_images")),List.of(),more(prompt)),
            section("settings","生成设置","FIELDS","AVAILABLE",null,fields("模型",task.get("model"),"服务商",task.get("provider_code"),"时长（秒）",task.get("duration_seconds"),"比例",task.get("aspect_ratio"),"分辨率",task.get("resolution"),"运动强度",task.get("motion_strength"),"镜头运动",task.get("camera_movement"),"随机种子",task.get("random_seed")),List.of(),false),
            section("results","生成结果","VIDEO",results.isEmpty()?"PENDING":"AVAILABLE",null,List.of(),media(results,"video_url"),false));
    }

    private List<Map<String,Object>> analysisSections(long tenant,long id) {
        Map<String,Object> task=one("select script_version_id,workflow_code,current_stage,overall_progress,error_message from script_analysis_task where tenant_id=? and id=?",tenant,id);
        Map<String,Object> version=one("select content,version_no from script_version where tenant_id=? and id=?",tenant,number(task,"script_version_id"));
        List<Map<String,Object>> stages=sql.queryForList("select stage_code,status,progress_percent,current_action from script_analysis_stage where task_id=:task order by stage_order",Map.of("task",id));
        List<Map<String,Object>> outcomes=sql.queryForList("select result_type,status from script_analysis_result where task_id=:task order by id limit 20",Map.of("task",id));
        String source=text(version.get("content"));
        return List.of(section("submission","固定剧本版本","TEXT",available(source),source,fields("版本",version.get("version_no")),List.of(),more(source)),
            section("settings","分析设置","FIELDS","AVAILABLE",null,fields("工作流",task.get("workflow_code")),List.of(),false),
            section("results","分析结果","STRUCTURED",outcomes.isEmpty()?"PENDING":"AVAILABLE",null,fields("当前阶段",task.get("current_stage"),"进度",task.get("overall_progress"),"错误",failure(task.get("error_message"))),bounded(outcomes,stages),false));
    }

    private List<Map<String,Object>> reviewSections(long tenant,long id) {
        Map<String,Object> task=one("select script_version_id,review_mode,selected_dimensions_json,review_scope_type,review_scope_json,report_markdown,current_stage,overall_progress,error_message from review_task where tenant_id=? and id=?",tenant,id);
        Map<String,Object> version=one("select content,version_no,file_name from review_script_version where tenant_id=? and id=?",tenant,number(task,"script_version_id"));
        String source=text(version.get("content")), report=text(task.get("report_markdown"));
        return List.of(section("submission","审核原稿","TEXT",available(source),source,fields("版本",version.get("version_no"),"文件",version.get("file_name")),List.of(),more(source)),
            section("settings","审核设置","FIELDS","AVAILABLE",null,fields("模式",task.get("review_mode"),"范围",task.get("review_scope_type"),"维度",task.get("selected_dimensions_json"),"范围详情",task.get("review_scope_json")),List.of(),false),
            section("results","审核结果","TEXT",available(report),report,fields("当前阶段",task.get("current_stage"),"进度",task.get("overall_progress"),"错误",failure(task.get("error_message"))),List.of(),more(report)));
    }

    private List<Map<String,Object>> decompositionSections(long tenant,long id,String type) {
        if(type.equals("VIDEO_DECOMPOSITION")) {
            Map<String,Object> batch=one("select name,model_id,total_episodes,completed_episodes,failed_episodes from video_decomposition_batch where tenant_id=? and id=?",tenant,id);
            return List.of(section("submission","批次提交","FIELDS","AVAILABLE",null,fields("名称",batch.get("name"),"模型",batch.get("model_id"),"总集数",batch.get("total_episodes")),List.of(),false),
                section("results","批次进度","BUSINESS_STAGE","AVAILABLE",null,fields("完成",batch.get("completed_episodes"),"失败",batch.get("failed_episodes")),List.of(),false));
        }
        Map<String,Object> episode=one("select batch_id,episode_no,source_file_name,mime_type,file_size,duration_seconds,analysis_version,draft_content,draft_version,confirmed_script_version_id,error_message from video_decomposition_episode where tenant_id=? and id=?",tenant,id);
        Map<String,Object> result=one("select content,format_version from video_decomposition_script_result where tenant_id=? and episode_id=?",tenant,id);
        String output=first(text(result.get("content")),text(episode.get("draft_content")));
        return List.of(section("submission","源视频","FIELDS","AVAILABLE",null,fields("第几集",episode.get("episode_no"),"文件",episode.get("source_file_name"),"类型",episode.get("mime_type"),"大小",episode.get("file_size"),"时长",episode.get("duration_seconds")),List.of(),false),
            section("settings","处理设置","FIELDS","AVAILABLE",null,fields("分析版本",episode.get("analysis_version"),"草稿版本",episode.get("draft_version")),List.of(),false),
            section("results","剧本结果","TEXT",available(output),output,fields("格式版本",result.get("format_version"),"错误",failure(episode.get("error_message"))),List.of(),more(output)));
    }

    private List<Map<String,Object>> storyboardSections(long tenant,long id,String type) {
        if(type.equals("STORYBOARD_BATCH")) {
            Map<String,Object> batch=one("select name,script_id from storyboard_batch where tenant_id=? and id=?",tenant,id);
            return List.of(section("submission","批次提交","FIELDS","AVAILABLE",null,fields("名称",batch.get("name"),"剧本",batch.get("script_id")),List.of(),false),
                section("results","批次结果","BUSINESS_STAGE","PENDING",null,List.of(),List.of(),false));
        }
        Map<String,Object> item=one("select batch_id,episode_id,episode_no,execution_id from storyboard_batch_item where tenant_id=? and id=?",tenant,id);
        return List.of(section("submission","分集输入","FIELDS","AVAILABLE",null,fields("第几集",item.get("episode_no"),"分集",item.get("episode_id")),List.of(),false),
            section("results","分镜结果","STRUCTURED","NOT_RECORDED",null,fields("执行来源",item.get("execution_id")),List.of(),false));
    }

    private List<Map<String,Object>> operationSections(long tenant,long id) {
        Map<String,Object> task=one("select operation_type,script_version_id,result_type,result_id,status,error_message from script_ai_operation where tenant_id=? and id=?",tenant,id);
        Map<String,Object> input=one("select content,version_no from script_version where tenant_id=? and id=?",tenant,number(task,"script_version_id"));
        String source=text(input.get("content"));
        String result="";
        if("SCRIPT_VERSION".equals(task.get("result_type"))) result=text(one("select content from script_version where tenant_id=? and id=?",tenant,number(task,"result_id")).get("content"));
        return List.of(section("submission","固定输入","TEXT",available(source),source,fields("输入版本",input.get("version_no")),List.of(),more(source)),
            section("settings","处理设置","FIELDS","AVAILABLE",null,fields("操作类型",task.get("operation_type"),"状态",task.get("status")),List.of(),false),
            section("results","本次结果","TEXT",available(result),result,fields("结果类型",task.get("result_type"),"错误",failure(task.get("error_message"))),List.of(),more(result)));
    }

    private Map<String,Object> one(String query,Object... args) { List<Map<String,Object>> rows=jdbc.queryForList(query,args); return rows.isEmpty()?Map.of():rows.get(0); }
    private static String text(Object value) { return value==null?"":String.valueOf(value); }
    private static String first(String a,String b) { return !a.isBlank()?a:b; }
    private static String failure(Object value) { return value==null||String.valueOf(value).isBlank()?null:"任务处理失败，请在原业务页面查看详情"; }
    private static String available(String value) { return value==null||value.isBlank()?"NOT_RECORDED":"AVAILABLE"; }
    private static boolean more(String value) { return value!=null&&value.length()>4000; }
    private static String preview(String value) { return value==null?null:value.length()>4000?value.substring(0,4000):value; }
    private static List<Map<String,Object>> fields(Object... values) {
        List<Map<String,Object>> result=new ArrayList<>();
        for(int i=0;i<values.length;i+=2) if(values[i+1]!=null && !String.valueOf(values[i+1]).isBlank()) result.add(Map.of("label",String.valueOf(values[i]),"value",String.valueOf(values[i+1])));
        return result;
    }
    private static List<Map<String,Object>> media(List<Map<String,Object>> rows,String urlKey) {
        return rows.stream().map(row->{Map<String,Object> item=new LinkedHashMap<>(); item.put("id",row.get("id")); item.put("url",row.get(urlKey)); item.put("thumbnailUrl",row.get("thumbnail_url")); item.put("width",row.get("width")); item.put("height",row.get("height")); item.put("selected",truth(row.get("is_selected"))); return item;}).toList();
    }
    private static List<Map<String,Object>> bounded(List<Map<String,Object>>... groups) {
        List<Map<String,Object>> result=new ArrayList<>(); for(List<Map<String,Object>> group:groups) for(Map<String,Object> item:group) if(result.size()<20) result.add(item); return result;
    }
    private static Map<String,Object> section(String key,String title,String kind,String availability,String body,List<Map<String,Object>> fields,List<Map<String,Object>> items,boolean hasMore) {
        Map<String,Object> section=new LinkedHashMap<>(); section.put("key",key);section.put("title",title);section.put("kind",kind);section.put("availability",availability);section.put("preview",preview(body));section.put("fields",fields);section.put("items",items);section.put("hasMore",hasMore);return section;
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
