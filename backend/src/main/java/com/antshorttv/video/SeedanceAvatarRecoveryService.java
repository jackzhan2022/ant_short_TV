package com.antshorttv.video;

import com.antshorttv.avatar.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SeedanceAvatarRecoveryService {
    private final JdbcTemplate jdbc;
    private final AiVideoTaskMapper videos;
    private final ArkAssetPool pool;
    private final ObjectMapper json;
    private final ObjectProvider<AiVideoTaskService> videoService;
    private final boolean enabled;
    private ObjectProvider<com.antshorttv.freecreation.FreeCreationAvatarRecoveryService> freeCreations;
    @org.springframework.beans.factory.annotation.Autowired
    public void setFreeCreationRecovery(ObjectProvider<com.antshorttv.freecreation.FreeCreationAvatarRecoveryService> recovery) {this.freeCreations=recovery;}
    public SeedanceAvatarRecoveryService(JdbcTemplate jdbc,AiVideoTaskMapper videos,ArkAssetPool pool,ObjectMapper json,
        ObjectProvider<AiVideoTaskService> videoService,@Value("${ark.avatar.recovery-enabled:true}") boolean enabled) {
        this.jdbc=jdbc;this.videos=videos;this.pool=pool;this.json=json;this.videoService=videoService;this.enabled=enabled;
    }
    /** Invoked in the original failure transaction, after that attempt's settlement. */
    public void enqueue(AiVideoTaskEntity source) {
        if(!enabled || source.avatarRecoverySourceTaskId!=null || !"FAILED".equals(source.status)
            || !com.antshorttv.ai.VolcengineProviderCodes.isArk(source.providerCode)
            || !SeedanceRealPersonRejection.matches(source.errorMessage)) return;
        try {
            jdbc.update("insert into seedance_avatar_recovery(source_task_id,tenant_id,project_id,storyboard_id,created_by,prepared_snapshot_json,next_poll_at,deadline_at) values(?,?,?,?,?,?,now(),?)",
                source.id,source.tenantId,source.projectId,source.storyboardId,source.createdBy,source.requestSnapshotJson,LocalDateTime.now().plusDays(1));
        } catch(DuplicateKeyException duplicate) { /* One automatic recovery per original task. */ }
    }
    public Map<String,Object> get(long tenant,long project,long source) {
        var rows=jdbc.queryForList("select * from seedance_avatar_recovery where tenant_id=? and project_id=? and source_task_id=?",tenant,project,source);
        if(rows.isEmpty()) return null;
        var row=rows.get(0);var result=new LinkedHashMap<String,Object>();
        for(String key:List.of("id","status","phase")) result.put(key,row.get(key));
        result.put("sourceTaskId",source);result.put("retryTaskId",row.get("retry_task_id"));result.put("errorMessage",row.get("error_message"));return result;
    }
    public void pollDue() {
        // The feature flag stops new workflows; drain admitted work so manual ingestion cannot stay blocked.
        var ids=jdbc.queryForList("select id from seedance_avatar_recovery where status in ('PENDING','RUNNING') and (next_poll_at is null or next_poll_at<=now()) and (claim_until is null or claim_until<now()) order by case when id in (select admitted_recovery_id from ark_asset_scope where admitted_recovery_id is not null) then 0 else 1 end,coalesce(next_poll_at,created_at),id limit 5",Long.class);
        for(long id:ids) process(id);
    }
    public void process(long id) {
        String token=UUID.randomUUID().toString();
        if(jdbc.update("update seedance_avatar_recovery set status='RUNNING',claim_token=?,claim_until=?,updated_at=now() where id=? and status in ('PENDING','RUNNING') and (claim_until is null or claim_until<now())",token,LocalDateTime.now().plusMinutes(5),id)!=1)return;
        try {
            var job=jdbc.queryForMap("select * from seedance_avatar_recovery where id=?",id);
            if(((java.sql.Timestamp)job.get("deadline_at")).toLocalDateTime().isBefore(LocalDateTime.now())) throw new IllegalStateException("素材准备超过 24 小时，请检查素材库容量和处理状态后重试。");
            boolean freeCreation=job.get("source_creation_id")!=null;
            Long tenant,project;String sourceError;
            if(freeCreation) {
                var source=freeCreations.getObject().requireSource(job);
                tenant=source.tenantId;project=null;sourceError=(String)job.get("source_error_message");
            } else {
                var source=videos.selectById(((Number)job.get("source_task_id")).longValue());
                if(source==null || source.deletedAt!=null || !"FAILED".equals(source.status)) throw new IllegalStateException("原视频任务已删除或状态已变化，自动恢复已停止。");
                tenant=source.tenantId;project=source.projectId;sourceError=source.errorMessage;
            }
            if(!pool.configured()) throw new IllegalStateException("虚拟人像素材库尚未配置 AK/SK，无法自动入库。");
            JsonNode frozen=json.readTree(String.valueOf(job.get("prepared_snapshot_json")));
            if(!(frozen instanceof ObjectNode snapshot))throw new IllegalStateException("原视频请求快照不可用，无法自动准备素材。");
            List<JsonNode> eligible=eligibleImages(snapshot,sourceError);
            if(freeCreation)pool.reserveFreeCreationRecovery(id,tenant,eligible);
            else pool.reserveRecovery(id,tenant,project,eligible);
            int images=0;
            for(JsonNode reference:eligible) {
                if(!"IMAGE".equalsIgnoreCase(reference.path("reference").path("mediaType").asText()))continue;
                images++;
                if(reference.path("avatarPrepared").asBoolean(false))continue;
                phase(id,token,"上传参考图片至虚拟人像库");
                String label=reference.path("displayName").asText(reference.path("reference").path("displayName").asText(freeCreation?"自由创作参考图":"分镜参考图"));
                String path=reference.path("objectStoragePath").asText(null);
                String url=reference.path("providerUrl").asText(reference.path("rawUrl").asText(null));
                String existing=reference.path("providerAssetId").asText(null);
                var asset=freeCreation?pool.prepareFreeCreationImage(id,tenant,label,path,url,existing)
                    :pool.prepareImage(id,tenant,project,label,path,url,existing);
                var item=(ObjectNode)reference;
                item.put("providerAssetId",asset.assetId());
                item.put("avatarPrepared","Active".equals(asset.status()));
                jdbc.update("update seedance_avatar_recovery set prepared_snapshot_json=?,phase=?,updated_at=now() where id=? and claim_token=?",snapshot.toString(),"Active".equals(asset.status())?"参考图片已就绪，继续准备视频":"等待素材处理完成",id,token);
                return; // One image per claim bounds provider I/O and allows fair dispatch.
            }
            if(images==0) throw new IllegalStateException("原视频请求没有可入库的图片参考，请检查首帧和参考素材。");
            phase(id,token,freeCreation?"素材已就绪，重新创建自由创作视频":"素材已就绪，重新创建分镜视频");
            if(freeCreation) freeCreations.getObject().createRetry(id,token,snapshot);
            else videoService.getObject().createAvatarRecoveryTask(id,token,snapshot);
        } catch(ArkAssetPool.Waiting wait) {
            jdbc.update("update seedance_avatar_recovery set status='PENDING',phase=?,updated_at=now() where id=? and claim_token=?",wait.getMessage(),id,token);
        } catch(Exception e) {
            if(e instanceof java.io.IOException || e instanceof ArkAssetClient.ProviderException p && !p.definitive()) {
                jdbc.update("update seedance_avatar_recovery set status='PENDING',phase='素材状态暂时不可用，稍后继续确认',updated_at=now() where id=? and claim_token=?",id,token);
                return;
            }
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            String message=e.getMessage()==null?"素材准备失败，请稍后重试。":e.getMessage();
            message=message.replaceAll("([?&](?:token|signature|api_key|access_key|secret_key)=)[^&\\s]+","$1***");
            jdbc.update("update seedance_avatar_recovery set status='FAILED',phase='自动恢复未完成',error_message=?,updated_at=now() where id=? and claim_token=?",message.substring(0,Math.min(900,message.length())),id,token);
        } finally {
            if(jdbc.queryForObject("select count(*) from seedance_avatar_recovery where id=? and status in ('PENDING','RUNNING')",Long.class,id)==0)pool.releaseRecovery(id);
            jdbc.update("update seedance_avatar_recovery set claim_token=null,claim_until=null,next_poll_at=?,updated_at=now() where id=? and claim_token=?",LocalDateTime.now().plusSeconds(15),id,token);
        }
    }
    private List<JsonNode> eligibleImages(JsonNode snapshot,String error) {
        Set<Integer> rejected=new HashSet<>();
        var matcher=java.util.regex.Pattern.compile("content\\[(\\d{1,9})\\]").matcher(error==null?"":error);
        while(matcher.find())rejected.add(Integer.parseInt(matcher.group(1)));
        var result=new ArrayList<JsonNode>();int contentIndex=0;
        for(JsonNode item:snapshot.path("references")) {
            contentIndex++;
            JsonNode ref=item.path("reference");
            if(!"IMAGE".equalsIgnoreCase(ref.path("mediaType").asText()))continue;
            boolean alreadyVirtual=!item.path("providerAssetId").asText("").isBlank();
            String type=ref.path("assetType").asText("");
            if(alreadyVirtual || (!rejected.isEmpty()?rejected.contains(contentIndex):type.isBlank() || "CHARACTER".equalsIgnoreCase(type)))result.add(item);
        }
        return result;
    }
    private void phase(long id,String token,String phase) {
        if(jdbc.update("update seedance_avatar_recovery set phase=?,updated_at=now() where id=? and claim_token=? and claim_until>now()",phase,id,token)!=1)throw new ArkAssetPool.Waiting("恢复任务正在由其他节点继续处理。");
    }
}
