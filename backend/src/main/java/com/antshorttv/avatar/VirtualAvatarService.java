package com.antshorttv.avatar;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.*;
import com.antshorttv.querycache.QueryResultCache;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class VirtualAvatarService {
    private ArkAssetPool assetPool;
    @org.springframework.beans.factory.annotation.Autowired
    void setAssetPool(ArkAssetPool pool) { this.assetPool=pool; }
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final TenantContextResolver tenants;
    private final RbacPermissionService permissions;
    private final ArkAssetClient client;
    private final VirtualAvatarStorage storage;
    private final QueryResultCache cache;
    public VirtualAvatarService(JdbcTemplate jdbc,PlatformTransactionManager manager,TenantContextResolver tenants,
        RbacPermissionService permissions,ArkAssetClient client,VirtualAvatarStorage storage,QueryResultCache cache) {
        this.jdbc=jdbc;tx=new TransactionTemplate(manager);this.tenants=tenants;this.permissions=permissions;
        this.client=client;this.storage=storage;this.cache=cache;
    }
    private TenantContext access(long tenant,long project,boolean write) {
        var context=tenants.requireActiveMember(tenant);
        if(!permissions.hasProjectPermission(context,project,"ELEMENT:VIEW") || write &&
            (!permissions.hasProjectPermission(context,project,"ELEMENT:EDIT") || !permissions.hasProjectPermission(context,project,"AI_SERVICE:USE")))
            throw new BusinessException(ErrorCode.FORBIDDEN,"无权操作该项目的虚拟人像素材。");
        return context;
    }
    public List<Map<String,Object>> create(long tenant,long project,List<Long> variants) {
        var context=access(tenant,project,true);client.requireConfigured();
        if(variants==null || variants.isEmpty() || variants.size()>100 || variants.stream().anyMatch(Objects::isNull)) throw invalid("每次请选择 1–100 张形态图。");
        var results=new ArrayList<Map<String,Object>>();
        for(Long variant:new LinkedHashSet<>(variants)) {
            try { results.add(tx.execute(s->enqueue(context,project,variant))); }
            catch(BusinessException e) {results.add(Map.of("variantId",variant,"errorMessage",e.getMessage()));}
        }
        return results;
    }
    private Map<String,Object> enqueue(TenantContext context,long project,long variant) {
        long tenant=context.tenantId();
        var rows=jdbc.queryForList("select * from asset_visual_variant where tenant_id=? and project_id=? and id=? and deleted_at is null for update",tenant,project,variant);
        if(rows.isEmpty()) throw invalid("角色形态图不存在。");
        var v=rows.get(0);
        if(!"CHARACTER".equals(v.get("asset_type")) || !"COMPLETED".equals(v.get("generation_status")) || text(v,"current_image_url").isBlank()) throw invalid("仅支持已有图片的角色形态。");
        var characters=jdbc.queryForList("select name from character_asset where id=? and tenant_id=? and project_id=? and deleted_at is null",v.get("asset_id"),tenant,project);
        if(characters.isEmpty()) throw invalid("角色已删除。");
        var previous=jdbc.queryForList("select * from virtual_avatar_task where tenant_id=? and project_id=? and variant_id=? and source_image_url=? and coalesce(source_image_result_id,0)=? order by id desc limit 1",tenant,project,variant,v.get("current_image_url"),v.get("current_image_result_id")==null?0:v.get("current_image_result_id"));
        if(!previous.isEmpty()) {
            var old=previous.get(0);
            if(Set.of("QUEUED","RUNNING").contains(text(old,"status"))) return present(old);
            if("SUBMISSION_UNKNOWN".equals(text(old,"error_code")) && Objects.equals(old.get("source_image_url"),v.get("current_image_url")) && Objects.equals(old.get("source_image_result_id"),v.get("current_image_result_id"))) throw invalid("上次提交结果待核实，请先在火山控制台确认，避免重复入库。");
            if("FAILED".equals(text(old,"status")) && Objects.equals(old.get("source_image_url"),v.get("current_image_url")) && Objects.equals(old.get("source_image_result_id"),v.get("current_image_result_id"))) return present(old);
            if("SUCCEEDED".equals(text(old,"status")) && Objects.equals(v.get("virtual_avatar_asset_id"),old.get("provider_asset_id"))) return present(old);
        }
        if(!text(v,"virtual_avatar_asset_id").isBlank()) throw invalid("该图片已有虚拟人像资产 ID。");
        String path=null;
        if(v.get("current_image_result_id")!=null) {
            var images=jdbc.queryForList("select storage_path from ai_image_result where tenant_id=? and project_id=? and id=? and status='ACTIVE'",tenant,project,v.get("current_image_result_id"));
            if(images.isEmpty()) throw invalid("源图片已失效。");path=text(images.get(0),"storage_path");
        }
        String sourcePath=path,title=characters.get(0).get("name")+" · "+v.get("name")+" · 虚拟人像";
        var key=new GeneratedKeyHolder();
        jdbc.update(conn->{var ps=conn.prepareStatement("insert into virtual_avatar_task(tenant_id,project_id,variant_id,character_id,title,source_image_result_id,source_image_url,source_storage_path,provider_project,created_by,next_poll_at,poll_deadline_at) values(?,?,?,?,?,?,?,?,?,?,now(),?)",new String[]{"id"});
            Object[] values={tenant,project,variant,v.get("asset_id"),title,v.get("current_image_result_id"),v.get("current_image_url"),sourcePath,client.project(),context.userId(),LocalDateTime.now().plusDays(1)};
            for(int i=0;i<values.length;i++)ps.setObject(i+1,values[i]);return ps;},key);
        return present(require(tenant,project,Objects.requireNonNull(key.getKey()).longValue()));
    }
    public Map<String,Object> get(long tenant,long project,long id) {access(tenant,project,false);return present(require(tenant,project,id));}
    public List<Map<String,Object>> candidates(long tenant,long project,int page,Long variantId) {
        access(tenant,project,false);
        return jdbc.queryForList("""
            select v.id, c.name characterName, v.name variantName, v.current_image_url imageUrl,
                v.virtual_avatar_asset_id assetId, t.id taskId, t.status, t.phase, t.error_message errorMessage, t.retryable
            from asset_visual_variant v join character_asset c on c.id=v.asset_id and c.tenant_id=v.tenant_id and c.project_id=v.project_id and c.deleted_at is null
            left join virtual_avatar_task t on t.id=(select max(a.id) from virtual_avatar_task a where a.tenant_id=v.tenant_id and a.project_id=v.project_id and a.variant_id=v.id and a.source_image_url=v.current_image_url and coalesce(a.source_image_result_id,0)=coalesce(v.current_image_result_id,0))
            where v.tenant_id=? and v.project_id=? and v.asset_type='CHARACTER' and v.deleted_at is null and v.generation_status='COMPLETED'
                and v.current_image_url is not null and (? is null or v.id=?)
            order by c.id,v.id limit 100 offset ?
            """,tenant,project,variantId,variantId,(Math.max(1,page)-1L)*100).stream().map(row->{
            Map<String,Object> item=new LinkedHashMap<>();
            for(String key:List.of("id","characterName","variantName","imageUrl","assetId","taskId","status","phase","errorMessage","retryable")) item.put(key,row.get(key));
            return item;
        }).toList();
    }
    public Map<String,Object> retry(long tenant,long project,long id) {
        var context=access(tenant,project,true);client.requireConfigured();
        return tx.execute(s->{var task=require(tenant,project,id);
            jdbc.queryForList("select id from asset_visual_variant where tenant_id=? and project_id=? and id=? for update",tenant,project,task.get("variant_id"));
            if(!Objects.equals(number(task,"created_by"),context.userId()) || !"FAILED".equals(text(task,"status")) || !truth(task.get("retryable"))) throw invalid("该任务不可重试。");
            if(!sourceCurrent(task)) throw invalid("原图已更换，请为新图创建入库任务。");
            if(jdbc.queryForObject("select count(*) from virtual_avatar_task where tenant_id=? and project_id=? and variant_id=? and status in ('QUEUED','RUNNING')",Long.class,tenant,project,task.get("variant_id"))>0) throw invalid("该形态图已有进行中的任务。");
            boolean keep=Set.of("STORAGE_FAILED","QUERY_FAILED","POLL_TIMEOUT").contains(text(task,"error_code"));
            jdbc.update("update virtual_avatar_task set status='QUEUED',phase=?,provider_asset_id=?,ingestion_attempt=ingestion_attempt+?,error_code=null,error_message=null,retryable=false,completed_at=null,claim_token=null,claimed_at=null,next_poll_at=now(),poll_deadline_at=?,updated_at=now() where id=?",
                keep?"查询素材状态":"等待提交",keep?task.get("provider_asset_id"):null,keep?0:1,LocalDateTime.now().plusDays(1),id);
            return present(require(tenant,project,id));});
    }
    public List<Long> due() {
        return jdbc.query("select id from virtual_avatar_task where status in ('QUEUED','RUNNING') and (next_poll_at is null or next_poll_at<=now()) and (claim_token is null or claimed_at<?) order by coalesce(next_poll_at,created_at),id limit 10",(rs,n)->rs.getLong(1),LocalDateTime.now().minusMinutes(5));
    }
    public void process(long id) {
        String token=UUID.randomUUID().toString();
        if(jdbc.update("update virtual_avatar_task set claim_token=?,claimed_at=now() where id=? and status in ('QUEUED','RUNNING') and (claim_token is null or claimed_at<?)",token,id,LocalDateTime.now().minusMinutes(5))!=1)return;
        var task=jdbc.queryForMap("select * from virtual_avatar_task where id=?",id);
        String phase=text(task,"phase");
        try {
            if(!client.project().equals(text(task,"provider_project"))) {fail(id,token,"PROJECT_CHANGED","素材库项目配置已变化，请恢复原项目配置。",false);return;}
            if(!sourceCurrent(task)) {fail(id,token,"SOURCE_CHANGED","源形态图已更换或删除，请为当前图片重新提交。",false);return;}
            String asset=text(task,"provider_asset_id"),group=text(task,"provider_group_id");
            if(asset.isBlank()) {
                if(assetPool!=null && !group.isBlank() && !assetPool.groupUsable(group)) {
                    group="";jdbc.update("update virtual_avatar_task set provider_group_id=null where id=? and claim_token=?",id,token);
                }
                if("提交素材".equals(phase) || "创建素材组".equals(phase) && group.isBlank()) {fail(id,token,"SUBMISSION_UNKNOWN","提交结果尚未确认，请先在火山控制台核实。",false);return;}
                if(group.isBlank()) {
                    var groups=jdbc.queryForList("select provider_group_id from virtual_avatar_task where tenant_id=? and project_id=? and character_id=? and provider_project=? and provider_group_id is not null order by id desc limit 1",task.get("tenant_id"),task.get("project_id"),task.get("character_id"),client.project());
                    if(!groups.isEmpty() && (assetPool==null || assetPool.groupUsable(text(groups.get(0),"provider_group_id"))))group=text(groups.get(0),"provider_group_id");
                    else {phase="创建素材组";stage(id,token,phase,10);group=assetPool==null?client.createGroup(text(task,"title")):assetPool.createManualGroup(id,text(task,"title"));}
                    jdbc.update("update virtual_avatar_task set provider_group_id=? where id=? and claim_token=?",group,id,token);
                }
                phase="准备素材";stage(id,token,phase,15);
                String url=storage.sourceUrl(text(task,"source_storage_path"),text(task,"source_image_url"));
                phase="提交素材";stage(id,token,phase,20);
                asset=assetPool==null?client.createAsset(group,text(task,"title"),url):assetPool.createManualAsset(id,((Number)task.get("ingestion_attempt")).intValue(),group,text(task,"title"),url);
                jdbc.update("update virtual_avatar_task set provider_asset_id=?,phase='查询素材状态',progress=40,updated_at=now() where id=? and claim_token=?",asset,id,token);
                return;
            }
            phase="查询素材状态";stage(id,token,phase,50);
            var result=client.getAsset(asset);
            if("Processing".equals(result.status())) {
                if(task.get("poll_deadline_at") instanceof java.sql.Timestamp deadline && deadline.toLocalDateTime().isBefore(LocalDateTime.now()))
                    fail(id,token,"POLL_TIMEOUT","素材仍在处理中，可重试继续查询原资产。",true);
                return;
            }
            if("Failed".equals(result.status())) {
                if(assetPool!=null)assetPool.failed(asset);
                fail(id,token,ArkAssetClient.safeCode(result.errorCode()),"火山素材处理失败（"+ArkAssetClient.safeCode(result.errorCode())+"），请检查原图后重试。",true);return;
            }
            if(assetPool!=null)assetPool.active(asset);
            phase="保存素材结果";stage(id,token,phase,80);
            String path=storage.save(number(task,"tenant_id"),number(task,"project_id"),id,result.url());
            String completedAsset=asset;
            tx.executeWithoutResult(s->{
                jdbc.queryForList("select id from asset_visual_variant where tenant_id=? and project_id=? and id=? for update",task.get("tenant_id"),task.get("project_id"),task.get("variant_id"));
                if(!sourceCurrent(task)) {fail(id,token,"SOURCE_CHANGED","源形态图已更换，结果未绑定。",false);return;}
                if(jdbc.update("update virtual_avatar_task set status='SUCCEEDED',phase='入库完成',progress=100,result_storage_path=?,retryable=false,error_code=null,error_message=null,completed_at=now(),updated_at=now() where id=? and claim_token=?",path,id,token)!=1)return;
                jdbc.update("update asset_visual_variant set virtual_avatar_asset_id=?,updated_at=now() where tenant_id=? and project_id=? and id=? and deleted_at is null",completedAsset,task.get("tenant_id"),task.get("project_id"),task.get("variant_id"));
                cache.invalidateProjectAfterCommit(number(task,"tenant_id"),number(task,"project_id"),QueryResultCache.Region.SCRIPT);
            });
        } catch(Exception e) {
            if(e instanceof ArkAssetPool.Waiting wait) {stage(id,token,"等待容量："+wait.getMessage(),10);return;}
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            boolean submitting=Set.of("创建素材组","提交素材").contains(phase);
            String code=submitting ? e instanceof ArkAssetClient.ProviderException p && p.definitive() ? p.code() : "SUBMISSION_UNKNOWN"
                : phase.equals("保存素材结果") ? "STORAGE_FAILED" : phase.equals("准备素材") ? "SOURCE_URL_FAILED" : "QUERY_FAILED";
            fail(id,token,code,code.equals("SUBMISSION_UNKNOWN")?"提交响应未确认，请先在火山控制台核实，禁止直接重提。":phase+"失败（"+code+"）。",!code.equals("SUBMISSION_UNKNOWN"));
        } finally {
            jdbc.update("update virtual_avatar_task set claim_token=null,claimed_at=null,next_poll_at=? where id=? and claim_token=?",LocalDateTime.now().plusSeconds(15),id,token);
            cache.invalidateAfterCommit(QueryResultCache.Scope.tenant(number(task,"tenant_id")),QueryResultCache.Region.TASKS);
        }
    }
    private boolean sourceCurrent(Map<String,Object> task) {
        var rows=jdbc.queryForList("select v.current_image_result_id,v.current_image_url from asset_visual_variant v join character_asset c on c.id=v.asset_id and c.tenant_id=v.tenant_id and c.project_id=v.project_id where v.tenant_id=? and v.project_id=? and v.id=? and v.deleted_at is null and c.deleted_at is null",task.get("tenant_id"),task.get("project_id"),task.get("variant_id"));
        return !rows.isEmpty() && Objects.equals(rows.get(0).get("current_image_result_id"),task.get("source_image_result_id")) && Objects.equals(rows.get(0).get("current_image_url"),task.get("source_image_url"));
    }
    private void stage(long id,String token,String phase,int progress) {jdbc.update("update virtual_avatar_task set status='RUNNING',phase=?,progress=?,updated_at=now() where id=? and claim_token=?",phase,progress,id,token);}
    private void fail(long id,String token,String code,String message,boolean retry) {jdbc.update("update virtual_avatar_task set status='FAILED',error_code=?,error_message=?,retryable=?,completed_at=now(),updated_at=now() where id=? and claim_token=?",code,message,retry,id,token);}
    private Map<String,Object> require(long tenant,long project,long id) {
        var rows=jdbc.queryForList("select * from virtual_avatar_task where tenant_id=? and project_id=? and id=?",tenant,project,id);
        if(rows.isEmpty())throw new BusinessException(ErrorCode.NOT_FOUND,"虚拟人像任务不存在。");return rows.get(0);
    }
    private Map<String,Object> present(Map<String,Object> t) {
        var r=new LinkedHashMap<String,Object>();
        r.put("id",t.get("id"));r.put("taskKey","VIRTUAL_AVATAR:"+t.get("id"));r.put("variantId",t.get("variant_id"));r.put("status",t.get("status"));r.put("phase",t.get("phase"));r.put("progress",t.get("progress"));r.put("assetId",t.get("provider_asset_id"));r.put("errorCode",t.get("error_code"));r.put("errorMessage",t.get("error_message"));r.put("retryable",t.get("retryable"));return r;
    }
    private static String text(Map<String,Object> row,String key) {return row.get(key)==null?"":String.valueOf(row.get(key));}
    private static Long number(Map<String,Object> row,String key) {return ((Number)row.get(key)).longValue();}
    private static boolean truth(Object v) {return Boolean.TRUE.equals(v)||v instanceof Number n&&n.intValue()!=0;}
    private static BusinessException invalid(String message) {return new BusinessException(ErrorCode.VALIDATION_ERROR,message);}
}
