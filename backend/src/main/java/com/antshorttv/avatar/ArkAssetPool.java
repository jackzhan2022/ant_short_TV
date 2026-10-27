package com.antshorttv.avatar;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Shared account admission, managed-resource ownership and safe remote cache eviction. */
@Service
public class ArkAssetPool {
    public static class Waiting extends RuntimeException { public Waiting(String message) { super(message); } }
    public record PreparedAsset(Long resourceId, String assetId, String status) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ArkAssetClient client;
    private final ObjectMapper json;
    private final VirtualAvatarStorage storage;
    private final String scope;
    private final Set<String> configuredProjects;
    private final boolean cleanupEnabled;
    private com.antshorttv.querycache.QueryResultCache cache=com.antshorttv.querycache.QueryResultCache.disabled();
    @org.springframework.beans.factory.annotation.Autowired
    void setCache(com.antshorttv.querycache.QueryResultCache cache) {this.cache=cache;}
    private static final ScheduledExecutorService HEARTBEATS = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "ark-asset-lease"); thread.setDaemon(true); return thread;
    });

    public ArkAssetPool(JdbcTemplate jdbc, PlatformTransactionManager manager, ArkAssetClient client,
        ObjectMapper json, VirtualAvatarStorage storage,
        @Value("${ARK_ASSET_QUOTA_SCOPE:default}") String scope,
        @Value("${ARK_ASSET_PROJECT_NAMES:}") String projectNames,
        @Value("${ark.avatar.cleanup-enabled:true}") boolean cleanupEnabled) {
        this.jdbc=jdbc; this.tx=new TransactionTemplate(manager); this.client=client; this.json=json;
        this.storage=storage; this.scope=scope; this.cleanupEnabled=cleanupEnabled;
        this.configuredProjects=new LinkedHashSet<>();
        for (String name : projectNames.split(",")) if (!name.isBlank()) configuredProjects.add(name.trim());
    }

    public boolean configured() { return client.configured(); }
    /** Admit a whole storyboard before pinning partial inputs; serialize preparation to prevent quota deadlocks. */
    public void reserveRecovery(long recoveryId,int missingImages) throws Exception {
        reserveRecovery(recoveryId,() -> missingImages);
    }
    public void reserveRecovery(long recoveryId,long tenant,long project,List<JsonNode> images) throws Exception {
        reserveRecoveryImages(recoveryId,tenant,project,images);
    }
    public void reserveFreeCreationRecovery(long recoveryId,long tenant,List<JsonNode> images) throws Exception {
        reserveRecoveryImages(recoveryId,tenant,null,images);
    }
    private void reserveRecoveryImages(long recoveryId,long tenant,Long project,List<JsonNode> images) throws Exception {
        reserveRecovery(recoveryId,() -> {
            Set<String> needed=new HashSet<>();
            for(JsonNode image:images) {
                String existing=image.path("providerAssetId").asText("").replaceFirst("^asset://", "");
                if(!existing.isBlank()) {
                    Long owned=ownedId(existing);
                    if(owned==null)continue;
                    var found=row(owned);
                    if(!Set.of("EVICTED","FAILED").contains(text(found,"status"))) {
                        pin(owned,"recovery:"+recoveryId,null,recoveryId);continue;
                    }
                }
                String owner=imageKey(tenant,project,image.path("objectStoragePath").asText(null),image.path("providerUrl").asText(null));
                var matches=jdbc.queryForList("select * from ark_managed_resource where scope_key=? and provider_project=? and resource_type='ASSET' and owner_key=?",scope,client.project(),owner);
                if(!matches.isEmpty() && !Set.of("EVICTED","FAILED","NEW").contains(text(matches.get(0),"status"))) {
                    pin(((Number)matches.get(0).get("id")).longValue(),"recovery:"+recoveryId,null,recoveryId);
                } else needed.add(owner);
            }
            return needed.size();
        });
    }
    private void reserveRecovery(long recoveryId,java.util.function.IntSupplier required) throws Exception {
        try(Lease lease=lock()) {
            Long current=admittedRecovery();
            if(current!=null) {
                if(current==recoveryId)return;
                throw new Waiting("正在准备其他视频的素材，请排队等待容量。");
            }
            int missingImages=required.getAsInt();
            Inventory inventory=inventory(lease);reconcile(inventory);
            int target=Math.min(40,50-missingImages);
            if(cleanupEnabled && occupied(inventory,true)+missingImages>45) {
                reclaim(inventory,true,target,lease);inventory=inventory(lease);
            }
            if(occupied(inventory,true)+missingImages>50)
                throw new Waiting("火山素材容量不足以准备当前视频，等待安全清理或其他任务完成。");
            jdbc.update("update ark_asset_scope set admitted_recovery_id=? where scope_key=? and lease_token=?",recoveryId,scope,lease.token);
        }
    }
    public void releaseRecovery(long recoveryId) {
        jdbc.update("update ark_asset_scope set admitted_recovery_id=null where scope_key=? and admitted_recovery_id=?",scope,recoveryId);
    }
    private Long admittedRecovery() {
        Long owner=jdbc.queryForObject("select admitted_recovery_id from ark_asset_scope where scope_key=?",Long.class,scope);
        if(owner!=null && jdbc.queryForObject("select count(*) from seedance_avatar_recovery where id=? and status in ('PENDING','RUNNING')",Long.class,owner)==0) {
            releaseRecovery(owner);return null;
        }
        return owner;
    }
    public String createManualGroup(long taskId, String name) throws Exception {
        return createResource(resource("GROUP", "manual-group:"+taskId, null, null, null, null, name), null);
    }
    public String createManualAsset(long taskId, String group, String name, String url) throws Exception {
        return createManualAsset(taskId,0,group,name,url);
    }
    public String createManualAsset(long taskId,int attempt,String group,String name,String url) throws Exception {
        return createResource(resource("ASSET", "manual-asset:"+taskId+":"+attempt, null, null, null, url, name), group);
    }
    public void failed(String assetId) {
        jdbc.update("update ark_managed_resource set status='FAILED',updated_at=now() where scope_key=? and resource_type='ASSET' and provider_id=? and status not in ('DELETING','EVICTED')",scope,assetId);
    }
    public boolean groupUsable(String group) {
        return jdbc.queryForObject("select count(*) from ark_managed_resource where scope_key=? and resource_type='GROUP' and provider_id=? and status in ('EVICTED','DELETING')",Long.class,scope,group)==0;
    }
    public void active(String assetId) {
        jdbc.update("update ark_managed_resource set status='ACTIVE',last_used_at=now(),updated_at=now() where scope_key=? and resource_type='ASSET' and provider_id=? and status not in ('DELETING','EVICTED')",scope,assetId);
    }

    public PreparedAsset prepareImage(long recoveryId, long tenant, long project, String label,
        String path, String url, String existingAssetId) throws Exception {
        return prepareImageInScope(recoveryId,tenant,project,label,path,url,existingAssetId);
    }
    public PreparedAsset prepareFreeCreationImage(long recoveryId,long tenant,String label,
        String path,String url,String existingAssetId) throws Exception {
        return prepareImageInScope(recoveryId,tenant,null,label,path,url,existingAssetId);
    }
    private PreparedAsset prepareImageInScope(long recoveryId,long tenant,Long project,String label,
        String path,String url,String existingAssetId) throws Exception {
        if (existingAssetId!=null && !existingAssetId.isBlank()) {
            String id=existingAssetId.replaceFirst("^asset://", "");
            try {
                var existing=client.getAsset(id);
                if ("Active".equals(existing.status())) {
                    Long owned=ownedId(id);
                    if (owned==null || !"EVICTED".equals(text(row(owned),"status"))) {
                        if (owned!=null) pin(owned,"recovery:"+recoveryId,null,recoveryId);
                        return new PreparedAsset(owned,id,"Active");
                    }
                }
                if ("Processing".equals(existing.status())) {
                    Long owned=ownedId(id);if(owned!=null)pin(owned,"recovery:"+recoveryId,null,recoveryId);
                    return new PreparedAsset(owned,id,"Processing");
                }
                if ("Failed".equals(existing.status())) throw new IllegalStateException("参考图片素材处理失败："+ArkAssetClient.safeCode(existing.errorCode()));
            } catch (ArkAssetClient.ProviderException e) {
                if (!notFound(e)) throw e;
                Long owned=ownedId(id);if(owned!=null)evicted(row(owned));
            }
        }
        if ((path==null || path.isBlank()) && (url==null || url.isBlank() || url.startsWith("asset://")))
            throw new IllegalStateException("参考图片原文件不可用，无法重新入库。");
        String key=imageKey(tenant,project,path,url);
        long resource=resource("ASSET",key,tenant,project,path,url,label);
        pin(resource,"recovery:"+recoveryId,null,recoveryId);
        var row=row(resource);
        String id=text(row,"provider_id");
        if (id.isBlank() || "EVICTED".equals(text(row,"status"))) {
            long group=resource("GROUP","shared-group",null,null,null,null,"分镜虚拟人像");
            id=createResource(resource,createResource(group,null,recoveryId),recoveryId);
        }
        ArkAssetClient.Asset result;
        try {result=client.getAsset(id);}
        catch(ArkAssetClient.ProviderException missing) {
            if(notFound(missing)) {evicted(row(resource));throw new Waiting("旧素材已失效，正在重新入库。");}
            throw missing;
        }
        if ("Failed".equals(result.status())) {
            jdbc.update("update ark_managed_resource set status='FAILED',error_message=?,updated_at=now() where id=?",safe(result.errorMessage()),resource);
            throw new IllegalStateException("参考图片素材处理失败："+ArkAssetClient.safeCode(result.errorCode()));
        }
        if ("Active".equals(result.status())) active(id);
        return new PreparedAsset(resource,id,result.status());
    }

    /** Called inside the video creation transaction; deletion claims lock the same rows. */
    public void pinVideo(long taskId, JsonNode snapshot) { pinMedia(taskId,null,snapshot); }
    public void pinFreeCreation(long creationId, JsonNode snapshot) { pinMedia(null,creationId,snapshot); }
    public void releaseFreeCreation(long creationId) {
        jdbc.update("delete from ark_asset_consumer where free_creation_id=?",creationId);
    }
    private void pinMedia(Long taskId,Long creationId,JsonNode snapshot) {
        for (JsonNode ref : snapshot.path("references")) {
            String id=ref.path("providerAssetId").asText("").replaceFirst("^asset://", "");
            if (id.isBlank()) continue;
            Long resource=ownedId(id);
            if (resource==null) {
                var manual=jdbc.queryForList("select * from virtual_avatar_task where provider_asset_id=? order by id desc limit 1",id);
                if(!manual.isEmpty()) {adopt(manual.get(0),"ASSET","provider_asset_id",text(manual.get(0),"provider_group_id"));resource=ownedId(id);}
            }
            if (resource!=null) pin(resource,creationId==null?"video:"+taskId:"free-creation:"+creationId,taskId,null,creationId);
        }
    }
    private Long ownedId(String id) {
        var rows=jdbc.queryForList("select id from ark_managed_resource where scope_key=? and resource_type='ASSET' and provider_id=?",scope,id);
        return rows.isEmpty()?null:((Number)rows.get(0).get("id")).longValue();
    }
    private void pin(long resource, String key, Long video, Long recovery) { pin(resource,key,video,recovery,null); }
    private void pin(long resource, String key, Long video, Long recovery,Long creation) {
        tx.executeWithoutResult(s -> {
            var rows=jdbc.queryForList("select status from ark_managed_resource where id=? for update",resource);
            if (rows.isEmpty() || "DELETING".equals(text(rows.get(0),"status"))
                || (video!=null || creation!=null) && "EVICTED".equals(text(rows.get(0),"status")))
                throw new Waiting("素材已被回收，正在等待重新入库。");
            if (jdbc.queryForObject("select count(*) from ark_asset_consumer where resource_id=? and consumer_key=?",Long.class,resource,key)==0)
                jdbc.update("insert into ark_asset_consumer(resource_id,consumer_key,video_task_id,recovery_id,free_creation_id) values(?,?,?,?,?)",resource,key,video,recovery,creation);
            jdbc.update("update ark_managed_resource set last_used_at=now() where id=?",resource);
        });
    }

    private long resource(String type,String owner,Long tenant,Long project,String path,String url,String name) {
        return tx.execute(s -> {
            var old=jdbc.queryForList("select id,status from ark_managed_resource where scope_key=? and provider_project=? and resource_type=? and owner_key=? for update",scope,client.project(),type,owner);
            if (!old.isEmpty()) {
                if(!"FAILED".equals(text(old.get(0),"status")))return ((Number)old.get(0).get("id")).longValue();
                jdbc.update("update ark_managed_resource set owner_key=? where id=?",owner+":failed:"+old.get(0).get("id"),old.get(0).get("id"));
            }
            var key=new GeneratedKeyHolder();
            try {
                jdbc.update(c -> {
                    var p=c.prepareStatement("insert into ark_managed_resource(scope_key,provider_project,resource_type,owner_key,provider_name,tenant_id,project_id,source_storage_path,source_url) values(?,?,?,?,?,?,?,?,?)",new String[]{"id"});
                    p.setString(1,scope);p.setString(2,client.project());p.setString(3,type);p.setString(4,owner);
                    String title=name==null?"素材":name;
                    p.setString(5,title.substring(0,Math.min(35,title.length()))+"-av-"+digest(owner).substring(0,16));
                    p.setObject(6,tenant);p.setObject(7,project);p.setString(8,path);p.setString(9,url);return p;
                },key);
                return key.getKey().longValue();
            } catch (DuplicateKeyException race) {
                return jdbc.queryForObject("select id from ark_managed_resource where scope_key=? and provider_project=? and resource_type=? and owner_key=?",Long.class,scope,client.project(),type,owner);
            }
        });
    }
    private String createResource(long id,String group) throws Exception {return createResource(id,group,null);}
    private String createResource(long id,String group,Long recoveryId) throws Exception {
        try (Lease lease=lock()) {
            Long admitted=admittedRecovery();
            if(admitted!=null && !Objects.equals(admitted,recoveryId))
                throw new Waiting("正在为当前视频保留素材余量，其他入库任务排队中。");
            var row=row(id);
            if (!text(row,"provider_id").isBlank() && !Set.of("EVICTED","DELETING","FAILED").contains(text(row,"status"))) return text(row,"provider_id");
            if ("DELETING".equals(text(row,"status"))) throw new Waiting("素材正在回收，等待回收确认。");
            Inventory inventory=inventory(lease);
            reconcile(inventory);
            row=row(id);
            if (!text(row,"provider_id").isBlank() && !Set.of("EVICTED","FAILED").contains(text(row,"status"))) return text(row,"provider_id");
            if (Set.of("UNKNOWN","SUBMITTING").contains(text(row,"status"))) throw new Waiting("上次素材提交结果待确认，已保留额度。");
            boolean asset="ASSET".equals(text(row,"resource_type"));
            if (cleanupEnabled && occupied(inventory,asset)>=45) { reclaim(inventory,asset,40,lease); inventory=inventory(lease); }
            if (occupied(inventory,asset)+1>50) throw new Waiting(asset?"火山素材容量不足，等待安全清理或其他任务完成。":"火山素材组容量不足，等待空组回收。");
            if (asset) {
                if(!groupUsable(group))throw new Waiting("素材组已被回收，等待重新建立。");
                int rateAdmitted=jdbc.update("update ark_asset_scope set next_create_at=?,updated_at=now() where scope_key=? and lease_token=? and (next_create_at is null or next_create_at<=now())",LocalDateTime.now().plusSeconds(21),scope,lease.token);
                if (rateAdmitted!=1) throw new Waiting("素材入库排队中，请稍候。");
            }
            String source=asset?storage.sourceUrl(nullable(row,"source_storage_path"),text(row,"source_url")):null;
            lease.require();
            jdbc.update("update ark_managed_resource set status='SUBMITTING',provider_id=null,provider_group_id=?,error_message=null,updated_at=now() where id=?",group,id);
            try {
                String remote=asset?client.createAsset(group,text(row,"provider_name"),source):client.createGroup(text(row,"provider_name"));
                lease.require();
                jdbc.update("update ark_managed_resource set provider_id=?,status=?,last_used_at=now(),updated_at=now() where id=?",remote,asset?"PROCESSING":"ACTIVE",id);
                return remote;
            } catch (Exception e) {
                boolean definitive=e instanceof ArkAssetClient.ProviderException p && p.definitive();
                jdbc.update("update ark_managed_resource set status=?,error_message=?,updated_at=now() where id=?",definitive?"NEW":"UNKNOWN",safe(e.getMessage()),id);
                if(e instanceof ArkAssetClient.ProviderException p) {
                    String code=p.code().toLowerCase(Locale.ROOT);
                    if(code.contains("limit") || code.contains("quota") || code.contains("exceed") || code.contains("429"))
                        throw new Waiting("素材库容量或请求频率受限，等待重新盘点后继续。");
                }
                throw e;
            }
        }
    }

    public void maintain() throws Exception {
        if (!client.configured()) return;
        try (Lease lease=lock()) {
            Inventory inventory=inventory(lease);
            adoptManualResources(inventory);
            reconcile(inventory);
            if (cleanupEnabled) {
                retryDeletions(lease);
                inventory=inventory(lease);
                if (occupied(inventory,true)>=45) reclaim(inventory,true,40,lease);
                inventory=inventory(lease);
                if (occupied(inventory,false)>=45) reclaim(inventory,false,40,lease);
            }
        }
    }
    private record Inventory(Map<String,JsonNode> assets,Map<String,JsonNode> groups) {}
    private Inventory inventory(Lease lease) throws Exception {
        Set<String> projects=new LinkedHashSet<>(configuredProjects);projects.add(client.project());
        projects.addAll(jdbc.queryForList("select distinct provider_project from virtual_avatar_task",String.class));
        projects.addAll(jdbc.queryForList("select distinct provider_project from ark_managed_resource where scope_key=?",String.class,scope));
        var assets=new LinkedHashMap<String,JsonNode>();var groups=new LinkedHashMap<String,JsonNode>();
        for (String project:projects) {
            lease.require();
            for (JsonNode item:client.listAssets(project)) {
                if (item.path("Id").asText().isBlank()) throw new Waiting("素材库存返回不完整，暂缓提交。");
                assets.put(project+"/"+item.path("Id").asText(),item);
            }
            lease.require();
            for (JsonNode item:client.listGroups(project)) {
                if (item.path("Id").asText().isBlank()) throw new Waiting("素材组库存返回不完整，暂缓提交。");
                groups.put(project+"/"+item.path("Id").asText(),item);
            }
        }
        lease.require();
        jdbc.update("update ark_asset_scope set last_inventory_at=now(),asset_count=?,group_count=?,updated_at=now() where scope_key=? and lease_token=?",assets.size(),groups.size(),scope,lease.token);
        return new Inventory(assets,groups);
    }
    private int occupied(Inventory inventory,boolean asset) {
        var remote=asset?inventory.assets:inventory.groups;
        int count=remote.size();
        for(var row:jdbc.queryForList("select * from ark_managed_resource where scope_key=? and resource_type=? and status not in ('NEW','EVICTED')",scope,asset?"ASSET":"GROUP"))
            if(!remote.containsKey(text(row,"provider_project")+"/"+text(row,"provider_id"))) count++;
        return count;
    }
    private void reconcile(Inventory inventory) {
        for(var remote:inventory.assets.entrySet()) {
            String last=remote.getValue().path("LastInferenceTime").asText("");
            if(last.isBlank())continue;
            try {
                LocalDateTime used=LocalDateTime.ofInstant(OffsetDateTime.parse(last).toInstant(),ZoneId.systemDefault());
                String project=remote.getKey().substring(0,remote.getKey().lastIndexOf('/'));
                jdbc.update("update ark_managed_resource set last_used_at=? where scope_key=? and provider_project=? and resource_type='ASSET' and provider_id=? and last_used_at<?",used,scope,project,remote.getValue().path("Id").asText(),used);
            } catch(java.time.format.DateTimeParseException invalid) {
                throw new Waiting("素材最近使用时间返回异常，暂缓清理。");
            }
        }
        for (var row:jdbc.queryForList("select * from ark_managed_resource where scope_key=? and status in ('UNKNOWN','SUBMITTING','DELETING')",scope)) {
            var remote="ASSET".equals(text(row,"resource_type"))?inventory.assets:inventory.groups;
            String key=text(row,"provider_project")+"/"+text(row,"provider_id");
            if ("DELETING".equals(text(row,"status"))) {
                if (!remote.containsKey(key)) evicted(row);
                continue;
            }
            var matches=remote.entrySet().stream().filter(e -> e.getKey().startsWith(text(row,"provider_project")+"/") && text(row,"provider_name").equals(e.getValue().path("Name").asText())).toList();
            if (matches.size()==1) jdbc.update("update ark_managed_resource set provider_id=?,status=?,error_message=null,updated_at=now() where id=?",matches.get(0).getValue().path("Id").asText(),"ASSET".equals(text(row,"resource_type"))?"PROCESSING":"ACTIVE",row.get("id"));
        }
    }
    private void reclaim(Inventory inventory,boolean assets,int target,Lease lease) throws Exception {
        var rows=jdbc.queryForList("select * from ark_managed_resource where scope_key=? and resource_type=? and provider_id is not null and status in ('ACTIVE','PROCESSING','FAILED','DELETING') and ((created_at<? and last_used_at<?) or status in ('FAILED','DELETING')) order by case when status='FAILED' then 0 else 1 end,last_used_at,id",scope,assets?"ASSET":"GROUP",LocalDateTime.now().minusMinutes(10),LocalDateTime.now().minusMinutes(10));
        int remaining=occupied(inventory,assets);
        for (var row:rows) {
            if(remaining<=target) break;
            if(!assets && inventory.assets.entrySet().stream().anyMatch(e -> e.getKey().startsWith(text(row,"provider_project")+"/") && text(row,"provider_id").equals(e.getValue().path("GroupId").asText()))) continue;
            boolean claimed=Boolean.TRUE.equals(tx.execute(s -> {
                jdbc.queryForList("select id from ark_managed_resource where id=? for update",row.get("id"));
                if(inUse(row)) return false;
                jdbc.update("update ark_managed_resource set deletion_previous_status=case when status='DELETING' then deletion_previous_status else status end,status='DELETING',updated_at=now() where id=?",row.get("id"));return true;
            }));
            if(!claimed) continue;
            if(deleteClaimed(row,lease))remaining--;
        }
    }
    private void retryDeletions(Lease lease) throws Exception {
        for(var pending:jdbc.queryForList("select * from ark_managed_resource where scope_key=? and status='DELETING' and provider_id is not null order by updated_at,id",scope)) {
            if(!inUse(pending))deleteClaimed(pending,lease);
        }
    }
    private boolean deleteClaimed(Map<String,Object> row,Lease lease) throws Exception {
        lease.require();
        try {
            if("GROUP".equals(text(row,"resource_type")) && client.listAssets(text(row,"provider_project")).stream().anyMatch(asset -> text(row,"provider_id").equals(asset.path("GroupId").asText()))) {
                jdbc.update("update ark_managed_resource set status=coalesce(deletion_previous_status,'ACTIVE'),deletion_previous_status=null,last_used_at=now(),updated_at=now() where id=?",row.get("id"));return false;
            }
            if("ASSET".equals(text(row,"resource_type")))client.deleteAsset(text(row,"provider_project"),text(row,"provider_id"));
            else client.deleteGroup(text(row,"provider_project"),text(row,"provider_id"));
            evicted(row);return true;
        } catch(Exception e) {
            if(e instanceof ArkAssetClient.ProviderException p && notFound(p)) {evicted(row);return true;}
            if(e instanceof ArkAssetClient.ProviderException p && p.definitive())
                jdbc.update("update ark_managed_resource set status=coalesce(deletion_previous_status,'ACTIVE'),deletion_previous_status=null,error_message=?,updated_at=now() where id=?",safe(e.getMessage()),row.get("id"));
            else jdbc.update("update ark_managed_resource set error_message=?,updated_at=now() where id=?",safe(e.getMessage()),row.get("id"));
            return false;
        }
    }
    private boolean inUse(Map<String,Object> row) {
        Object id=row.get("id");String provider=text(row,"provider_id");
        if(jdbc.queryForObject("select count(*) from ark_asset_consumer c join free_creation f on f.id=c.free_creation_id join ai_execution_task e on e.id=f.execution_id where c.resource_id=? and (e.status in ('PENDING','RUNNING') or (f.provider_started_at is not null and f.provider_result_json is null))",Long.class,id)>0)return true;
        if(jdbc.queryForObject("select count(*) from ark_asset_consumer c left join ai_video_task v on v.id=c.video_task_id left join seedance_avatar_recovery r on r.id=c.recovery_id where c.resource_id=? and (((v.deleted_at is null and v.status in ('PENDING','SUBMITTING','GENERATING')) or v.external_status in ('SUBMIT_UNKNOWN','QUERY_UNKNOWN')) or r.status in ('PENDING','RUNNING'))",Long.class,id)>0) return true;
        if("ASSET".equals(text(row,"resource_type"))) {
            if(jdbc.queryForObject("select count(*) from virtual_avatar_task where provider_project=? and provider_asset_id=? and status in ('QUEUED','RUNNING')",Long.class,row.get("provider_project"),provider)>0) return true;
            // Also protect legacy tasks created before consumers were introduced.
            for(String snapshot:jdbc.queryForList("select request_snapshot_json from ai_video_task where (deleted_at is null and status in ('PENDING','SUBMITTING','GENERATING')) or external_status in ('SUBMIT_UNKNOWN','QUERY_UNKNOWN')",String.class)) {
                try {for(JsonNode ref:json.readTree(snapshot==null?"{}":snapshot).path("references")) if(provider.equals(ref.path("providerAssetId").asText().replaceFirst("^asset://",""))) return true;}
                catch(Exception e) {return true;}
            }
        } else {
            if(jdbc.queryForObject("select count(*) from ark_managed_resource where scope_key=? and provider_project=? and provider_group_id=? and status not in ('EVICTED','NEW')",Long.class,scope,row.get("provider_project"),provider)>0) return true;
            if(jdbc.queryForObject("select count(*) from virtual_avatar_task where provider_project=? and provider_group_id=? and status in ('QUEUED','RUNNING')",Long.class,row.get("provider_project"),provider)>0) return true;
        }
        return false;
    }
    private void evicted(Map<String,Object> row) {
        tx.executeWithoutResult(s -> {
            jdbc.update("update ark_managed_resource set status='EVICTED',error_message=null,updated_at=now() where id=?",row.get("id"));
            if("ASSET".equals(text(row,"resource_type"))) {
                var affected=jdbc.queryForList("select distinct tenant_id,project_id from asset_visual_variant where virtual_avatar_asset_id=?",row.get("provider_id"));
                jdbc.update("update asset_visual_variant set virtual_avatar_asset_id=null,updated_at=now() where virtual_avatar_asset_id=?",row.get("provider_id"));
                for(var project:affected)cache.invalidateProjectAfterCommit(((Number)project.get("tenant_id")).longValue(),((Number)project.get("project_id")).longValue(),com.antshorttv.querycache.QueryResultCache.Region.SCRIPT);
            }
        });
    }
    private void adoptManualResources(Inventory inventory) {
        for(var task:jdbc.queryForList("select * from virtual_avatar_task where provider_group_id is not null")) {
            String prefix=text(task,"provider_project")+"/";
            if(inventory.groups.containsKey(prefix+text(task,"provider_group_id")))adopt(task,"GROUP","provider_group_id",null);
            if(inventory.assets.containsKey(prefix+text(task,"provider_asset_id")))adopt(task,"ASSET","provider_asset_id",text(task,"provider_group_id"));
        }
    }
    private void adopt(Map<String,Object> task,String type,String field,String group) {
        String id=text(task,field);
        if(jdbc.queryForObject("select count(*) from ark_managed_resource where scope_key=? and provider_project=? and resource_type=? and provider_id=?",Long.class,scope,task.get("provider_project"),type,id)>0)return;
        try {jdbc.update("insert into ark_managed_resource(scope_key,provider_project,resource_type,owner_key,provider_id,provider_group_id,provider_name,tenant_id,project_id,status,created_at,last_used_at) values(?,?,?,?,?,?,?,?,?,'ACTIVE',?,?)",scope,task.get("provider_project"),type,"adopted:"+id,id,group,text(task,"title"),task.get("tenant_id"),task.get("project_id"),task.get("created_at"),task.get("updated_at"));}
        catch(DuplicateKeyException ignored) {}
    }
    private Lease lock() {
        try {jdbc.update("insert into ark_asset_scope(scope_key) values(?)",scope);} catch(DuplicateKeyException ignored) {}
        String token=UUID.randomUUID().toString();
        if(jdbc.update("update ark_asset_scope set lease_token=?,lease_until=? where scope_key=? and (lease_until is null or lease_until<now())",token,LocalDateTime.now().plusMinutes(2),scope)!=1)
            throw new Waiting("素材库正在处理其他任务，请稍候。");
        return new Lease(token);
    }
    private class Lease implements AutoCloseable {
        final String token;final ScheduledFuture<?> heartbeat;
        Lease(String token) {this.token=token;heartbeat=HEARTBEATS.scheduleAtFixedRate(() -> {
            try {jdbc.update("update ark_asset_scope set lease_until=? where scope_key=? and lease_token=?",LocalDateTime.now().plusMinutes(2),scope,token);} catch(Exception ignored) {}
        },20,20,TimeUnit.SECONDS);}
        void require() {if(jdbc.queryForObject("select count(*) from ark_asset_scope where scope_key=? and lease_token=? and lease_until>now()",Long.class,scope,token)!=1)throw new Waiting("素材库处理租约已失效，等待重新确认状态。");}
        public void close() {heartbeat.cancel(false);jdbc.update("update ark_asset_scope set lease_token=null,lease_until=null where scope_key=? and lease_token=?",scope,token);}
    }
    private Map<String,Object> row(long id) {return jdbc.queryForMap("select * from ark_managed_resource where id=?",id);}
    private static boolean notFound(ArkAssetClient.ProviderException e) {return e.definitive() && (e.code().toLowerCase(Locale.ROOT).contains("notfound") || e.code().contains("NotFound") || e.code().equals("HTTP_404"));}
    private static String imageKey(long tenant,Long project,String path,String url) {return "image:"+tenant+":"+project+":"+digest(path!=null && !path.isBlank()?"path:"+path:"url:"+url);}
    private static String digest(String source) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String text(Map<String,Object> row,String key) {return row.get(key)==null?"":String.valueOf(row.get(key));}
    private static String nullable(Map<String,Object> row,String key) {String value=text(row,key);return value.isBlank()?null:value;}
    private static String safe(String value) {if(value==null)return "素材库请求失败。";return value.substring(0,Math.min(900,value.length())).replaceAll("([?&](?:token|signature|api_key|access_key|secret_key)=)[^&\\s]+","$1***");}
}
