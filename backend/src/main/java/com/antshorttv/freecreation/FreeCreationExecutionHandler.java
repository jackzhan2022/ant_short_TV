package com.antshorttv.freecreation;

import com.antshorttv.accounting.*;
import com.antshorttv.ai.*;
import com.antshorttv.execution.*;
import com.antshorttv.points.*;
import com.antshorttv.storage.ObjectStorageService;
import com.antshorttv.video.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FreeCreationExecutionHandler extends AiExecutionHandler {
    private final FreeCreationMapper creations;
    private final FreeCreationService service;
    private final FreeCreationMediaService media;
    private final AiInvocationService invocations;
    private final AiVideoProviderAdapter videoProvider;
    private final SeedanceArkVideoProviderAdapter ark;
    private final AiVideoTaskService videos;
    private final ObjectStorageService storage;
    private final AiExecutionService executions;
    private final AiExecutionTaskMapper executionMapper;
    private final AiExecutionClaimService claims;
    private final AiPointReservationMapper reservations;
    private final AiPointSettlementService settlements;
    private final AiUsageAccountingService accounting;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final org.springframework.transaction.support.TransactionTemplate transactions;

    public FreeCreationExecutionHandler(FreeCreationMapper creations,FreeCreationService service,FreeCreationMediaService media,
        AiInvocationService invocations,AiVideoProviderAdapter videoProvider,SeedanceArkVideoProviderAdapter ark,
        AiVideoTaskService videos,ObjectStorageService storage,AiExecutionService executions,AiExecutionTaskMapper executionMapper,
        AiExecutionClaimService claims,AiPointReservationMapper reservations,AiPointSettlementService settlements,
        AiUsageAccountingService accounting,ObjectMapper json,JdbcTemplate jdbc,org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.creations=creations;this.service=service;this.media=media;this.invocations=invocations;this.videoProvider=videoProvider;
        this.ark=ark;this.videos=videos;this.storage=storage;this.executions=executions;this.executionMapper=executionMapper;
        this.claims=claims;this.reservations=reservations;this.settlements=settlements;this.accounting=accounting;this.json=json;this.jdbc=jdbc;
        this.transactions=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }
    @Override public String scene() {return "free_creation_image";}
    @Override public List<String> scenes() {return List.of("free_creation_image","free_creation_video");}
    @Override public List<String> phases() {return List.of("SUBMIT","QUERY","STORE");}

    @Override public AiExecutionHandlerResult execute(AiExecutionContext context) {
        FreeCreationEntity creation=creations.selectById(context.task().businessId);
        if(creation==null || !context.task().id.equals(creation.executionId)
            || !context.task().tenantId.equals(creation.tenantId)) throw new IllegalStateException("自由创作执行归属不正确。");
        try {
            claims.requireActive(context.claim());
            if(creation.createdAt.isBefore(LocalDateTime.now().minusMinutes(40))) {
                settle(context,creation,creation.providerStartedAt==null?AiSettlementOutcome.PRE_CALL_CANCELED:AiSettlementOutcome.TIMED_OUT); throw new IllegalStateException("生成超时，请稍后重新提交。");
            }
            if(creation.providerResultJson==null) {
                if(creation.providerStartedAt!=null && creation.externalTaskId==null) {
                    settle(context,creation,AiSettlementOutcome.TRANSPORT_UNKNOWN);
                    throw new IllegalStateException("上次生成请求结果待核对，已暂停自动重试，避免重复扣费。");
                }
                if("IMAGE".equals(creation.mediaType)) generateImage(context,creation);
                else generateVideo(context,creation);
            }
            JsonNode result=service.read(creation.providerResultJson);
            if("VIDEO".equals(creation.mediaType) && !"SUCCEEDED".equals(result.path("status").asText())) {
                settle(context,creation,result.path("completionTokens").asLong()>0?AiSettlementOutcome.PROVIDER_BILLED_FAILURE:AiSettlementOutcome.TRANSPORT_UNKNOWN);
                throw new IllegalStateException("视频服务商生成失败，请调整提示词后重试。");
            }
            recordUsage(context,creation);
            phase(context,"STORE");
            try {
                if("IMAGE".equals(creation.mediaType)) {
                    int index=0;for(JsonNode url:result.path("images")) {
                        claims.requireActive(context.claim());media.store(creation,index++,url.asText(),null,context);
                    }
                } else {
                    var duration=result.path("durationSeconds");
                    media.store(creation,0,result.path("videoUrl").asText(),duration.isNumber()?duration.decimalValue():null,context);
                }
            } catch(RuntimeException storageFailure) {
                claims.requireActive(context.claim());
                // The provider response is durable; only storage/renditions are retried, never generation.
                throw new AiExecutionDeferredException("生成文件正在保存，请稍候。");
            }
            claims.requireActive(context.claim());settle(context,creation,AiSettlementOutcome.SUCCESS);
            return new AiExecutionHandlerResult("FREE_CREATION",creation.id);
        } catch(AiExecutionDeferredException | AiExecutionClaimLostException deferred) {throw deferred;}
        catch(AiGatewayException failure) {
            claims.requireActive(context.claim());
            creation.billingCallLogId=failure.getAiCallLogId();creation.billingAttemptId=context.claim().attemptId();save(context,creation);
            settle(context,creation,creation.externalTaskId==null && isRejection(failure)?AiSettlementOutcome.PROVIDER_REJECTION:AiSettlementOutcome.TRANSPORT_UNKNOWN);
            throw new IllegalStateException(FreeCreationErrorMessages.forProviderFailure(failure.getMessage()));
        } catch(RuntimeException failure) {
            settle(context,creation,creation.providerResultJson!=null?AiSettlementOutcome.BUSINESS_FAILURE:
                creation.providerStartedAt==null?AiSettlementOutcome.PRE_CALL_CANCELED:AiSettlementOutcome.TRANSPORT_UNKNOWN);
            throw failure;
        }
    }

    private void generateImage(AiExecutionContext context,FreeCreationEntity creation) {
        JsonNode params=service.read(creation.parametersJson);
        List<String> urls=media.items(creation.id,"REFERENCE").stream()
            .map(ref->storage.modelAccessUrl(ref.objectKey,Duration.ofHours(1))).toList();
        var request=base(context,creation,AiCapability.IMAGE).imageRequest(new AiImageRequest(creation.prompt,
            params.path("negativePrompt").asText(null),params.path("size").asText(null),params.path("aspectRatio").asText(),
            params.path("count").asInt(),urls,params.path("quality").asText(),params.path("outputFormat").asText())).build();
        beginProvider(context,creation);
        var response=invocations.invokeImage(request);
        claims.requireActive(context.claim());
        if(response.response()==null || response.response().imageUrls()==null || response.response().imageUrls().isEmpty())
            throw new IllegalStateException("服务商未返回有效图片。");
        creation.billingAttemptId=context.claim().attemptId();creation.billingCallLogId=response.aiCallLogId();
        creation.providerResultJson=json.valueToTree(Map.of("images",response.response().imageUrls())).toString();save(context,creation);
    }

    private void generateVideo(AiExecutionContext context,FreeCreationEntity creation) {
        AiVideoTaskEntity task=service.videoTask(creation,true);
        var request=base(context,creation,AiCapability.VIDEO).build();
        boolean seedance=VolcengineProviderCodes.isArk(creation.providerCode);
        AiInvocationResult<AiVideoProviderAdapter.VideoResult> response;
        if(creation.externalTaskId==null) {
            beginProvider(context,creation);
            response=invocations.invokeProviderNative(request,creation.prompt,route->seedance
                ?ark.submit(route.providerConfig(),route.model(),task,request.idempotencyKey())
                :videoProvider.submit(route.providerConfig(),route.model(),task,request.idempotencyKey()));
        } else {
            response=invocations.invokeProviderNative(request,creation.externalTaskId,route->seedance
                ?ark.poll(route.providerConfig(),route.model(),creation.externalTaskId,request.idempotencyKey())
                :videoProvider.poll(route.providerConfig(),route.model(),creation.externalTaskId,request.idempotencyKey()));
        }
        claims.requireActive(context.claim());
        if(response.providerExecutionState()==AiProviderExecutionState.ACCEPTED) {
            if(response.externalTaskId()==null || response.externalTaskId().isBlank()) throw new IllegalStateException("视频服务商没有返回任务标识。");
            creation.externalTaskId=response.externalTaskId();
            if(creation.billingAttemptId==null) {creation.billingAttemptId=context.claim().attemptId();creation.billingCallLogId=response.aiCallLogId();}
            save(context,creation);phase(context,"QUERY");throw new AiExecutionDeferredException("视频正在生成。");
        }
        if(response.response()==null) throw new IllegalStateException("视频服务商未返回生成结果。");
        creation.billingAttemptId=context.claim().attemptId();
        creation.billingCallLogId=response.aiCallLogId();creation.providerResultJson=json.valueToTree(response.response()).toString();save(context,creation);
    }

    private void beginProvider(AiExecutionContext context,FreeCreationEntity creation) {
        if(creation.providerStartedAt==null) {
            creation.providerStartedAt=LocalDateTime.now(); save(context,creation);
        }
    }

    private AiInvocationRequest.Builder base(AiExecutionContext context,FreeCreationEntity creation,AiCapability capability) {
        return AiInvocationRequest.capability(capability).tenantId(creation.tenantId).userId(creation.creatorId)
            .projectId(null).taskId(creation.id).modelId(creation.modelId).businessSceneCode(context.task().scene)
            .traceId(context.task().traceId).executionId(context.task().id).attemptId(context.claim().attemptId())
            .executionVersion(context.task().executionVersion).phase(context.claim().phase())
            .idempotencyKey("free-creation:"+context.task().id+":v"+context.task().executionVersion+
                (creation.externalTaskId==null?":submit":":poll")).requestSummary(creation.prompt);
    }

    private Map<AiUsageMetric,BigDecimal> actual(FreeCreationEntity creation) {
        JsonNode result=service.read(creation.providerResultJson);
        if(creation.providerResultJson==null) return Map.of();
        if("IMAGE".equals(creation.mediaType)) return Map.of(AiUsageMetric.CALL,BigDecimal.ONE,
            AiUsageMetric.IMAGE,BigDecimal.valueOf(result.path("images").size()),
            AiUsageMetric.INPUT_IMAGE,BigDecimal.valueOf(Math.max(0,media.items(creation.id,"REFERENCE").size()-1)));
        if(VolcengineProviderCodes.isArk(creation.providerCode)) {
            if(!result.path("completionTokens").isIntegralNumber() || result.path("completionTokens").asLong()<=0) return Map.of();
            String resolution=result.path("resolution").asText("");
            if(!resolution.isBlank() && !resolution.equalsIgnoreCase(service.read(creation.parametersJson).path("resolution").asText())) return Map.of();
            return Map.of(AiUsageMetric.OUTPUT_TOKEN,result.path("completionTokens").decimalValue());
        }
        if(!"SUCCEEDED".equals(result.path("status").asText())) return Map.of();
        BigDecimal duration=result.path("durationSeconds").isNumber()?result.path("durationSeconds").decimalValue():
            BigDecimal.valueOf(service.read(creation.parametersJson).path("durationSeconds").asInt());
        return Map.of(AiUsageMetric.CALL,BigDecimal.ONE,AiUsageMetric.VIDEO_SECOND,duration);
    }

    private Map<String,String> dimensions(FreeCreationEntity creation) {
        JsonNode p=service.read(creation.parametersJson);
        return "IMAGE".equals(creation.mediaType)?Map.of("aspectRatio",p.path("aspectRatio").asText(),"quality",p.path("quality").asText()):
            videos.videoDimensions(service.videoTask(creation,false));
    }

    private void recordUsage(AiExecutionContext context,FreeCreationEntity creation) {
        withClaim(context,()->recordUsageInternal(creation));
    }
    private void recordUsageInternal(FreeCreationEntity creation) {
        Map<AiUsageMetric,BigDecimal> usage=actual(creation); if(usage.isEmpty()) return;
        var correlation=new AiUsageContext(creation.tenantId,creation.executionId,creation.billingAttemptId,
            creation.billingCallLogId,creation.modelId);
        for(var entry:usage.entrySet()) {
            var dims=entry.getKey()==AiUsageMetric.CALL?Map.<String,String>of():dimensions(creation);
            var command=entry.getKey()==AiUsageMetric.OUTPUT_TOKEN?AiUsageCommand.providerReported(correlation,entry.getKey(),entry.getValue().toPlainString(),dims,LocalDateTime.now()):
                entry.getKey()==AiUsageMetric.CALL || entry.getKey()==AiUsageMetric.INPUT_IMAGE?AiUsageCommand.requestDerived(correlation,entry.getKey(),entry.getValue().toPlainString(),dims,LocalDateTime.now()):
                    AiUsageCommand.resultMeasured(correlation,entry.getKey(),entry.getValue().toPlainString(),dims,LocalDateTime.now());
            accounting.recordIfAbsent(command);
        }
        var cost=accounting.priceExecution(creation.executionId,usage.keySet());
        executionMapper.update(null,new UpdateWrapper<AiExecutionTaskEntity>().set("usage_cost_status",cost.status().name())
            .set("provider_cost_summary_json",json.valueToTree(cost.totalsByCurrency()).toString()).eq("id",creation.executionId));
    }

    private void settle(AiExecutionContext context,FreeCreationEntity creation,AiSettlementOutcome outcome) {
        withClaim(context,()->settleInternal(context,creation,outcome));
    }
    private void settleInternal(AiExecutionContext context,FreeCreationEntity creation,AiSettlementOutcome outcome) {
        var reservation=reservations.selectByExecutionId(creation.executionId);
        if(reservation==null || !"RESERVED".equals(reservation.status)) return;
        Map<AiUsageMetric,BigDecimal> usage=actual(creation);
        if(outcome==AiSettlementOutcome.SUCCESS && usage.isEmpty()) outcome=AiSettlementOutcome.TRANSPORT_UNKNOWN;
        if(!usage.isEmpty()) recordUsageInternal(creation);
        var settled=settlements.finalizeOutcome(reservation.id,outcome,usage,
            creation.billingAttemptId==null?context.claim().attemptId():creation.billingAttemptId,creation.billingCallLogId,
            "free-creation:"+creation.id+":v"+context.task().executionVersion+":settle");
        executions.updateSettlementSummary(settled);
    }

    private void withClaim(AiExecutionContext context,Runnable work) {
        transactions.executeWithoutResult(status->{
            var task=executions.requireTaskForUpdate(context.task().id);
            if(!"RUNNING".equals(task.status) || !Objects.equals(context.claim().claimToken(),task.claimToken)) throw new AiExecutionClaimLostException(task.id);
            work.run();
        });
    }

    private void save(AiExecutionContext context,FreeCreationEntity creation) {
        claims.requireActive(context.claim());
        int changed=creations.update(creation,new UpdateWrapper<FreeCreationEntity>().eq("id",creation.id)
            .apply("exists (select 1 from ai_execution_task e where e.id={0} and e.status='RUNNING' and e.claim_token={1})",
                context.task().id,context.claim().claimToken()));
        if(changed!=1) throw new AiExecutionClaimLostException(context.task().id);
    }
    private void phase(AiExecutionContext context,String phase) {
        claims.requireActive(context.claim());
        executionMapper.update(null,new UpdateWrapper<AiExecutionTaskEntity>().set("phase",phase)
            .eq("id",context.task().id).eq("claim_token",context.claim().claimToken()));
    }
    private boolean isRejection(AiGatewayException failure) {
        return Set.of("AI_AUTH_FAILED","AI_MODEL_NOT_FOUND","AI_MODEL_DISABLED","AI_PROVIDER_DISABLED","VALIDATION_ERROR").contains(failure.getErrorCode().name());
    }

    /** Recover terminal tasks left by a process crash; uncertain provider charges remain reviewable. */
    @Scheduled(fixedDelayString="${ai.execution.free-creation-reconcile-ms:60000}")
    public void reconcileTerminalReservations() {
        var ids=jdbc.queryForList("select f.id from free_creation f join ai_execution_task e on e.id=f.execution_id join ai_point_reservation r on r.execution_id=e.id where e.status in ('CANCELED','TIMED_OUT','FAILED') and r.status='RESERVED' order by f.id limit 50",Long.class);
        for(Long id:ids) {
            var creation=creations.selectById(id);var task=executions.requireTask(creation.executionId);
            var claim=new AiExecutionClaim(task.id,creation.billingAttemptId,null,task.executionVersion,task.phase);
            transactions.executeWithoutResult(transaction->{
                var latest=executions.requireTaskForUpdate(task.id);
                if(!Set.of("CANCELED","TIMED_OUT","FAILED").contains(latest.status)) return;
                settleInternal(new AiExecutionContext(latest,claim),creation,creation.providerStartedAt==null?AiSettlementOutcome.PRE_CALL_CANCELED:
                    creation.providerResultJson==null?AiSettlementOutcome.TRANSPORT_UNKNOWN:AiSettlementOutcome.BUSINESS_FAILURE);
            });
        }
    }
}
