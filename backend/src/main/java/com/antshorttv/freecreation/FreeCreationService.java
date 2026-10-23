package com.antshorttv.freecreation;

import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.ai.*;
import com.antshorttv.common.*;
import com.antshorttv.execution.*;
import com.antshorttv.points.AiPointEstimatePlan;
import com.antshorttv.script.StoryboardPromptCompiler;
import com.antshorttv.security.*;
import com.antshorttv.storage.ObjectStorageService;
import com.antshorttv.video.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FreeCreationService {
    private final FreeCreationMapper creations;
    private final FreeCreationMediaMapper mediaMapper;
    private final FreeCreationMediaService media;
    private final TenantContextResolver tenants;
    private final AiModelRouter router;
    private final AiExecutionService executions;
    private final AiVideoTaskService videos;
    private final SeedanceVideoRequestValidator videoValidator;
    private final ObjectStorageService storage;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final Validator validator;

    public FreeCreationService(FreeCreationMapper creations,FreeCreationMediaMapper mediaMapper,FreeCreationMediaService media,
        TenantContextResolver tenants,AiModelRouter router,AiExecutionService executions,
        AiVideoTaskService videos,SeedanceVideoRequestValidator videoValidator,ObjectStorageService storage,
        ObjectMapper json,JdbcTemplate jdbc,Validator validator) {
        this.creations=creations; this.mediaMapper=mediaMapper; this.media=media; this.tenants=tenants;
        this.router=router; this.executions=executions; this.videos=videos; this.videoValidator=videoValidator;
        this.storage=storage; this.json=json; this.jdbc=jdbc; this.validator=validator;
    }

    public TenantContext authorize(Long tenantId) { return tenants.requireActiveMember(tenantId); }

    public record ModelOption(Long id,Long providerId,String providerName,String name,String description,
        String serviceType,String status,Boolean isDefault,Integer sort,List<String> capabilities,String configJson) {}
    public record ProviderOption(Long id,String code,String status) {}
    public record ModelCatalog(List<ModelOption> models,List<ProviderOption> providers) {}

    public ModelCatalog models(Long tenantId) {
        authorize(tenantId);
        List<ModelOption> models=new ArrayList<>();
        Map<Long,ProviderOption> providers=new LinkedHashMap<>();
        var candidates=jdbc.queryForList("select id,service_type from ai_model where status='ENABLED' and service_type in ('IMAGE','VIDEO') order by sort desc,id desc");
        for(var candidate:candidates) {
            AiModelRoute route;
            try { route=router.route(((Number)candidate.get("id")).longValue(),(String)candidate.get("service_type")); }
            catch(AiGatewayException unavailable) { continue; }
            var model=route.model(); var provider=route.provider();
            models.add(new ModelOption(model.getId(),provider.getId(),provider.getName(),model.getName(),model.getDescription(),
                model.getServiceType(),model.getStatus(),model.getIsDefault(),model.getSort(),
                List.of(model.getServiceType()+"_GENERATION"),creationConfig(model.getConfigJson())));
            providers.put(provider.getId(),new ProviderOption(provider.getId(),provider.getCode(),provider.getStatus()));
        }
        return new ModelCatalog(models,List.copyOf(providers.values()));
    }

    private String creationConfig(String value) {
        if(value==null || value.isBlank()) return value;
        try {
            JsonNode config=json.readTree(value);
            if(!config.isObject()) return "null";
            ObjectNode safe=json.createObjectNode();
            for(String key:List.of("quality","outputFormat","videoGeneration")) if(config.has(key)) safe.set(key,config.get(key));
            return safe.toString();
        } catch(Exception invalidConfig) { return "null"; }
    }

    @Transactional
    public Map<String,Object> create(Long tenantId,FreeCreationRequest request) {
        TenantContext context=authorize(tenantId); validate(request);
        if(request.requestId()==null || !request.requestId().matches("[A-Za-z0-9_-]{16,100}")) throw invalid("请提供有效请求标识。");
        String hash=requestHash(request);
        // Serialize only creation writes within the team: a duplicate cannot reserve points twice.
        jdbc.queryForObject("select id from tenant where id=? for update",Long.class,tenantId);
        var existing=creations.selectOne(new QueryWrapper<FreeCreationEntity>().eq("tenant_id",tenantId)
            .eq("creator_id",context.userId()).eq("request_id",request.requestId()));
        if(existing!=null) {
            if(!hash.equals(existing.requestHash)) throw invalid("同一请求标识不能用于不同生成参数。");
            return present(context,existing);
        }
        Prepared prepared=prepare(context,request);
        FreeCreationEntity item=new FreeCreationEntity(); item.tenantId=tenantId; item.creatorId=context.userId();
        item.requestId=request.requestId(); item.requestHash=hash; item.mediaType=request.mediaType();
        item.modelId=prepared.route().model().getId(); item.modelName=prepared.route().model().getName();
        item.providerCode=prepared.route().provider().getCode(); item.prompt=request.prompt().trim();
        item.parametersJson=prepared.parameters().toString(); item.createdAt=LocalDateTime.now(); item.updatedAt=item.createdAt;
        creations.insert(item); media.attach(item.id,prepared.references());
        var execution=executions.createWithReservation(new AiExecutionCreateCommand(tenantId,context.userId(),null,
            "IMAGE".equals(item.mediaType)?"free_creation_image":"free_creation_video",item.mediaType,"FREE_CREATION",item.id,
            item.modelId,"SUBMIT","free:"+context.userId()+":"+request.requestId(),request.requestId(),true,"{}"),
            prepared.usage(),prepared.dimensions());
        item.executionId=execution.id; creations.updateById(item); return present(context,item);
    }

    public AiPointEstimatePlan pointEstimate(Long tenantId,JsonNode data) {
        TenantContext context=authorize(tenantId); FreeCreationRequest request;
        try { request=json.treeToValue(data,FreeCreationRequest.class); } catch(Exception ex) { throw invalid("自由创作参数格式不正确。"); }
        validate(request); Prepared prepared=prepare(context,request,true);
        var plan=new AiPointEstimatePlan("自由创作"+("IMAGE".equals(request.mediaType())?"图片":"视频"),request.modelId(),
            prepared.usage(),prepared.dimensions(),"VIDEO".equals(request.mediaType()));
        return "VIDEO".equals(request.mediaType())?plan.paidOnly():plan;
    }

    public static List<String> controlActions(String status) {
        return switch(status) {
            case "PENDING", "RUNNING" -> List.of("CANCEL");
            case "FAILED", "TIMED_OUT" -> List.of("RETRY");
            case "SUCCEEDED", "CANCELED" -> List.of("REGENERATE");
            default -> List.of();
        };
    }

    private FreeCreationEntity requireControl(TenantContext context,Long id) {
        var item=require(context,id);
        if(!context.userId().equals(item.creatorId)) throw new BusinessException(ErrorCode.FORBIDDEN,"只能操作自己提交的自由创作任务。");
        return item;
    }

    private void requireAction(AiExecutionTaskEntity task,String action) {
        if(!controlActions(task.status).contains(action)) throw invalid("当前任务状态不支持此操作。");
    }

    private FreeCreationRequest resubmission(FreeCreationEntity source,String requestId) {
        Map<String,Object> parameters=json.convertValue(read(source.parametersJson),new com.fasterxml.jackson.core.type.TypeReference<>() {});
        var references=media.items(source.id,"REFERENCE").stream()
            .map(ref->new FreeCreationRequest.Reference(ref.kind,null,ref.id)).toList();
        return new FreeCreationRequest(requestId,source.mediaType,source.modelId,source.prompt,parameters,references);
    }

    public AiPointEstimatePlan pointEstimateControl(Long tenantId,Long id,String action) {
        var source=requireControl(authorize(tenantId),id);
        requireAction(executions.requireTask(source.executionId),action);
        if(!Set.of("RETRY","REGENERATE").contains(action)) throw invalid("该操作无需积分预估。");
        return pointEstimate(tenantId,json.valueToTree(resubmission(source,"free-creation-estimate")));
    }

    /** A user-requested retry is a new, separately reserved generation; the old provider attempt stays immutable. */
    @Transactional
    public Map<String,Object> control(Long tenantId,Long id,String action,String idempotencyKey) {
        TenantContext context=authorize(tenantId);
        // Use the same lock order as create: team, then execution. It also serializes duplicate control requests.
        jdbc.queryForObject("select id from tenant where id=? for update",Long.class,tenantId);
        var source=requireControl(context,id);
        var task=executions.requireTaskForUpdate(source.executionId);
        if("CANCEL".equals(action)) {
            if("CANCELED".equals(task.status)) return present(context,source);
            requireAction(task,action);
            executions.cancel(task.id);
            return present(context,source);
        }
        if(!Set.of("RETRY","REGENERATE").contains(action)) throw invalid("任务操作无效。");
        if(idempotencyKey==null || idempotencyKey.isBlank() || idempotencyKey.length()>128) throw invalid("请提供有效操作标识。");
        String requestId="control-"+UUID.nameUUIDFromBytes((tenantId+":"+context.userId()+":"+id+":"+action+":"+idempotencyKey).getBytes(StandardCharsets.UTF_8));
        var existing=creations.selectOne(new QueryWrapper<FreeCreationEntity>().eq("tenant_id",tenantId)
            .eq("creator_id",context.userId()).eq("request_id",requestId));
        if(existing!=null) return present(context,existing);
        requireAction(task,action);
        return create(tenantId,resubmission(source,requestId));
    }

    private void validate(FreeCreationRequest request) {
        if(request==null || !validator.validate(request).isEmpty()) throw invalid("请检查媒体类型、模型、提示词与参考素材参数。");
    }

    Prepared prepare(TenantContext context,FreeCreationRequest request) {
        return prepare(context,request,false);
    }

    private Prepared prepare(TenantContext context,FreeCreationRequest request,boolean estimate) {
        AiModelRoute route=router.route(request.modelId(),request.mediaType());
        ObjectNode params=request.parameters()==null?json.createObjectNode():json.valueToTree(request.parameters());
        JsonNode config=read(route.model().getConfigJson());
        List<FreeCreationMediaEntity> refs=new ArrayList<>();
        for(var ref:request.references()==null?List.<FreeCreationRequest.Reference>of():request.references())
            refs.add(estimate && "IMAGE".equals(request.mediaType()) ? media.referenceForImageEstimate(context,ref) : media.reference(context,ref));
        Map<AiUsageMetric,BigDecimal> usage; Map<String,String> dimensions;
        if("IMAGE".equals(request.mediaType())) {
            allow(params,Set.of("count","aspectRatio","quality","outputFormat","size","negativePrompt"));
            for(var ref:refs) if(!"IMAGE".equals(ref.kind)) throw invalid("图片生成仅支持图片参考素材。");
            defaultValue(params,"count",1); defaultValue(params,"aspectRatio","1:1");
            defaultValue(params,"quality",config.path("quality").asText("STANDARD"));
            defaultValue(params,"outputFormat",config.path("outputFormat").asText("png"));
            requireInteger(params,"count",1,4);
            if("OpenAI".equalsIgnoreCase(route.provider().getCode()) && params.path("count").asInt()!=1)
                throw invalid("所选图片模型每次支持生成1张图片，请分次提交。");
            if("OpenAI".equalsIgnoreCase(route.provider().getCode()) && !"STANDARD".equalsIgnoreCase(params.path("quality").asText())
                && !XiongXiongAiAdapter.supportedQualities(route.model().getModelCode()).contains(params.path("quality").asText())) throw invalid("所选模型不支持此图片质量。");
            if(!Set.of("1:1","2:3","3:2","3:4","4:3","9:16","16:9").contains(params.path("aspectRatio").asText())) throw invalid("不支持的图片比例。");
            if(!Set.of("STANDARD","standard","auto","low","medium","high","hd","xhigh","max").contains(params.path("quality").asText())) throw invalid("不支持的图片质量。");
            if(!Set.of("png","jpeg","webp").contains(params.path("outputFormat").asText())) throw invalid("不支持的图片格式。");
            if(params.has("size") && !params.path("size").asText().matches("auto|[1-9][0-9]{2,3}x[1-9][0-9]{2,3}")) throw invalid("图片尺寸无效。");
            if(params.has("negativePrompt") && (!params.path("negativePrompt").isTextual() || params.path("negativePrompt").asText().length()>4000)) throw invalid("反向提示词过长。");
            if(refs.size()>14) throw invalid("参考图片不能超过14张。");
            usage=Map.of(AiUsageMetric.CALL,BigDecimal.ONE,AiUsageMetric.IMAGE,BigDecimal.valueOf(params.path("count").asInt()),
                AiUsageMetric.INPUT_IMAGE,BigDecimal.valueOf(Math.max(0,refs.size()-1)));
            dimensions=Map.of("aspectRatio",params.path("aspectRatio").asText(),"quality",params.path("quality").asText());
        } else {
            allow(params,Set.of("durationSeconds","resolution","intelligentDuration","aspectRatio","generateAudio","watermark"));
            defaultValue(params,"durationSeconds",5); defaultValue(params,"resolution","720p"); defaultValue(params,"aspectRatio","9:16");
            defaultValue(params,"generateAudio",true); defaultValue(params,"watermark",false); defaultValue(params,"intelligentDuration",false);
            for(String key:List.of("generateAudio","watermark","intelligentDuration")) if(!params.path(key).isBoolean()) throw invalid("视频开关参数格式不正确。");
            if(params.path("intelligentDuration").asBoolean()) params.put("durationSeconds",-1);
            if(!params.path("durationSeconds").isIntegralNumber() || !params.path("durationSeconds").canConvertToInt()) throw invalid("视频时长必须为整数。");
            if(!Set.of("1:1","3:4","4:3","9:16","16:9","21:9","adaptive").contains(params.path("aspectRatio").asText())) throw invalid("不支持的视频比例。");
            if("VOLCENGINE_ARK".equalsIgnoreCase(route.provider().getCode())) {
                if(params.path("durationSeconds").asInt()==-1 && !videoValidator.constraints(route.model()).path("duration").path("intelligent").asBoolean()) throw invalid("所选模型不支持智能时长。");
                videoValidator.validate(route.model(),params.path("durationSeconds").asInt(),params.path("resolution").asText(),resolved(refs,false));
            } else {
                requireInteger(params,"durationSeconds",1,60);
                if(!Set.of("480p","720p","1080p","STANDARD","HD").contains(params.path("resolution").asText())) throw invalid("不支持的视频分辨率。");
                if(refs.size()>1 || refs.stream().anyMatch(ref->!"IMAGE".equals(ref.kind))) throw invalid("该视频模型仅支持一张首帧参考图片。");
            }
            AiVideoTaskEntity task=videoTask(route.model().getId(),route.provider().getCode(),route.model().getModelCode(),request.prompt(),params,refs,false);
            int max=params.path("durationSeconds").asInt();
            if(max==-1) max=videoValidator.constraints(route.model()).path("duration").path("max").asInt();
            usage=videos.videoAuthorizedUsage(task,max); dimensions=videos.videoDimensions(task);
        }
        return new Prepared(route,params,refs,usage,dimensions);
    }

    public AiVideoTaskEntity videoTask(FreeCreationEntity creation,boolean urls) {
        String modelCode=urls?router.route(creation.modelId,"VIDEO").model().getModelCode():"";
        return videoTask(creation.modelId,creation.providerCode,modelCode,creation.prompt,
            read(creation.parametersJson),media.items(creation.id,"REFERENCE"),urls);
    }

    private AiVideoTaskEntity videoTask(Long modelId,String provider,String model,String prompt,JsonNode params,
        List<FreeCreationMediaEntity> refs,boolean urls) {
        AiVideoTaskEntity task=new AiVideoTaskEntity(); task.modelId=modelId; task.providerCode=provider; task.model=model;
        task.prompt=prompt; task.compiledPrompt=prompt; task.durationSeconds=params.path("durationSeconds").asInt();
        task.resolution=params.path("resolution").asText(); task.aspectRatio=params.path("aspectRatio").asText();
        task.generateAudio=params.path("generateAudio").asBoolean(); task.watermark=params.path("watermark").asBoolean();
        task.firstFrameUrl=refs.isEmpty()?"":urls?storage.modelAccessUrl(refs.get(0).objectKey,Duration.ofHours(1)):"";
        ObjectNode snapshot=json.createObjectNode(); snapshot.set("references",json.valueToTree(resolved(refs,urls)));
        task.requestSnapshotJson=snapshot.toString(); return task;
    }

    private List<VideoTaskReferenceResolver.ResolvedReference> resolved(List<FreeCreationMediaEntity> refs,boolean urls) {
        List<VideoTaskReferenceResolver.ResolvedReference> result=new ArrayList<>(); int index=0;
        for(var ref:refs) {
            index++;
            var reference=new StoryboardPromptCompiler.Reference(ref.kind,index,"参考"+index,"reference_"+ref.kind.toLowerCase(Locale.ROOT),
                "FREE_CREATION",ref.id,null,null,null,ref.fileName,index);
            result.add(new VideoTaskReferenceResolver.ResolvedReference(reference,ref.fileName,ref.objectKey,
                urls?storage.modelAccessUrl(ref.objectKey,Duration.ofHours(1)):"",ref.format,ref.fileSize,ref.width,ref.height,ref.durationSeconds,ref.fps));
        }
        return result;
    }

    public Map<String,Object> list(Long tenantId,String scope,String type,String status,String keyword,int current,int pageSize) {
        TenantContext context=authorize(tenantId);
        if(!Set.of("mine","team").contains(scope)) throw invalid("列表范围无效。");
        if(type!=null && !Set.of("IMAGE","VIDEO").contains(type)) throw invalid("媒体类型无效。");
        if(status!=null && !Set.of("QUEUED","RUNNING","SUCCEEDED","FAILED","CANCELED").contains(status)) throw invalid("任务状态无效。");
        if(current<1 || pageSize<1 || pageSize>100 || current>100000) throw invalid("分页参数无效。");
        String base=" from free_creation f join ai_execution_task e on e.id=f.execution_id left join app_user u on u.id=f.creator_id where f.tenant_id=?";
        List<Object> args=new ArrayList<>(List.of(tenantId));
        if("mine".equals(scope)) { base+=" and f.creator_id=?"; args.add(context.userId()); }
        if(type!=null) {base+=" and f.media_type=?";args.add(type);}
        if(status!=null) {base+=" and "+STATUS_SQL+"=?";args.add(status);}
        if(keyword!=null && !keyword.isBlank()) { if(keyword.length()>200) throw invalid("搜索内容过长。"); base+=" and (lower(f.prompt) like ? or lower(u.nickname) like ?)"; String query="%"+keyword.toLowerCase(Locale.ROOT)+"%";args.add(query);args.add(query);}
        Long total=jdbc.queryForObject("select count(*)"+base,Long.class,args.toArray());
        args.add(pageSize); args.add((current-1)*pageSize);
        var ids=jdbc.queryForList("select f.id"+base+" order by f.created_at desc,f.id desc limit ? offset ?",Long.class,args.toArray());
        return Map.of("records",ids.stream().map(id->present(context,creations.selectById(id))).toList(),"total",total,"current",current,"pageSize",pageSize);
    }

    public Map<String,Object> detail(Long tenantId,Long id) { var context=authorize(tenantId);return present(context,require(context,id)); }
    FreeCreationEntity require(TenantContext context,Long id) {
        var item=creations.selectById(id); if(item==null || !context.tenantId().equals(item.tenantId)) throw new BusinessException(ErrorCode.NOT_FOUND,"自由创作记录不存在。");return item;
    }
    public Map<String,Object> download(Long tenantId,Long id,Long outputId) {
        var context=authorize(tenantId); var creation=require(context,id);
        if (!"SUCCEEDED".equals(executions.requireTask(creation.executionId).status)) throw new BusinessException(ErrorCode.NOT_FOUND,"作品尚未发布。");
        var output=mediaMapper.selectById(outputId);
        if(output==null || !id.equals(output.creationId) || !"OUTPUT".equals(output.role) || !tenantId.equals(output.tenantId)) throw new BusinessException(ErrorCode.NOT_FOUND,"生成作品不存在。");
        return Map.of("url",media.grant(context,output,false),"fileName",output.fileName);
    }
    Map<String,Object> present(TenantContext context,FreeCreationEntity item) {
        var task=executions.requireTask(item.executionId); Map<String,Object> result=new LinkedHashMap<>();
        result.put("id",item.id); result.put("executionId",item.executionId); result.put("mediaType",item.mediaType);
        result.put("status",status(task,item));result.put("prompt",item.prompt);result.put("creatorId",item.creatorId);
        result.put("creatorName",jdbc.queryForObject("select nickname from app_user where id=?",String.class,item.creatorId));
        result.put("createdAt",item.createdAt); result.put("modelId",item.modelId); result.put("modelName",item.modelName);
        result.put("parameters",read(item.parametersJson)); result.put("references",media.items(item.id,"REFERENCE").stream().map(ref->media.present(context,ref)).toList());
        result.put("taskKey","FREE_CREATION:"+item.id);
        result.put("allowedActions",context.userId().equals(item.creatorId)?controlActions(task.status):List.of());
        result.put("outputs","SUCCEEDED".equals(task.status)?media.items(item.id,"OUTPUT").stream().map(output->media.present(context,output)).toList():List.of());
        result.put("errorMessage",Set.of("FAILED","TIMED_OUT").contains(task.status)?safeError(task.errorMessage):null); return result;
    }
    private static String safeError(String message) {
        if(message==null) return "生成失败，请重新提交。";
        String redacted=com.antshorttv.storage.SensitiveValueRedactor.redact(message).replaceAll("https?://[^\\s]+","[供应商地址]");
        return redacted.substring(0,Math.min(redacted.length(),300));
    }

    static String status(AiExecutionTaskEntity task,FreeCreationEntity item) {
        if("PENDING".equals(task.status)) return item.providerStartedAt==null?"QUEUED":"RUNNING";
        return "TIMED_OUT".equals(task.status)?"FAILED":task.status;
    }
    private static final String STATUS_SQL="case when e.status='PENDING' then case when f.provider_started_at is null then 'QUEUED' else 'RUNNING' end when e.status='TIMED_OUT' then 'FAILED' else e.status end";
    public JsonNode read(String value) { try {return value==null?json.createObjectNode():json.readTree(value);}catch(Exception ex){throw invalid("模型或任务参数无法读取。");} }
    private void defaultValue(ObjectNode node,String key,Object value) { if(!node.hasNonNull(key)) node.set(key,json.valueToTree(value)); }
    private void allow(ObjectNode params,Set<String> allowed) { params.fieldNames().forEachRemaining(key->{if(!allowed.contains(key))throw invalid("不支持的生成参数："+key);}); }
    private void requireInteger(ObjectNode node,String key,int min,int max) { if(!node.path(key).isIntegralNumber() || !node.path(key).canConvertToInt() || node.path(key).asInt()<min || node.path(key).asInt()>max)throw invalid("生成参数超出范围："+key); }
    private String requestHash(FreeCreationRequest request) {
        try {ObjectNode data=json.valueToTree(request);data.remove("requestId");
            Object canonical=json.convertValue(data,Object.class);
            String body=json.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true).writeValueAsString(canonical);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        }catch(Exception ex){throw invalid("生成参数格式不正确。");}
    }
    private static BusinessException invalid(String message) {return new BusinessException(ErrorCode.VALIDATION_ERROR,message);}
    record Prepared(AiModelRoute route,ObjectNode parameters,List<FreeCreationMediaEntity> references,Map<AiUsageMetric,BigDecimal> usage,Map<String,String> dimensions) {}
}
