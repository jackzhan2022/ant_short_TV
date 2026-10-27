package com.antshorttv.video;

import com.antshorttv.execution.*;
import com.antshorttv.material.MaterialFileAccessService;
import com.antshorttv.material.MediaPlaybackService;
import com.antshorttv.storage.*;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SubtitleEraseExecutionHandler extends AiExecutionHandler {
    private final SubtitleEraseTaskMapper tasks;
    private final AiVideoResultMapper results;
    private final AiVideoTaskMapper videos;
    private final AiExecutionTaskMapper executions;
    private final SubtitleEraseTaskService service;
    private final MediaKitSubtitleClient client;
    private final MediaPlaybackService playback;
    private final ObjectStorageService storage;
    private final ObjectStorageKeyFactory keys;
    private final MaterialFileAccessService files;
    private final AiVideoCoverService covers;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    public SubtitleEraseExecutionHandler(SubtitleEraseTaskMapper tasks,AiVideoResultMapper results,AiVideoTaskMapper videos,
        AiExecutionTaskMapper executions,SubtitleEraseTaskService service,MediaKitSubtitleClient client,MediaPlaybackService playback,
        ObjectStorageService storage,ObjectStorageKeyFactory keys,MaterialFileAccessService files,AiVideoCoverService covers,
        ObjectMapper json,PlatformTransactionManager manager) {
        this.tasks=tasks;this.results=results;this.videos=videos;this.executions=executions;this.service=service;
        this.client=client;this.playback=playback;this.storage=storage;this.keys=keys;this.files=files;this.covers=covers;this.json=json;
        this.tx=new TransactionTemplate(manager);
    }
    @Override public String scene(){return SubtitleEraseTaskService.SCENE;}
    @Override public List<String> phases(){return List.of("ERASE_SUBMIT","ERASE_PROCESSING","ERASE_PUBLISH");}
    @Override public AiExecutionRetryPolicy retryPolicy(Throwable failure) {
        return failure instanceof ProviderFailure ? AiExecutionRetryPolicy.none() : new AiExecutionRetryPolicy(3,Duration.ofSeconds(15));
    }
    @Override public AiExecutionHandlerResult execute(AiExecutionContext context) {
        var task=service.require(context.task().tenantId,context.task().projectId,context.task().businessId);
        if(!Objects.equals(task.executionId,context.task().id)) throw new IllegalStateException("字幕擦除任务执行记录不匹配。");
        var published=task.resultId==null?null:results.selectById(task.resultId);
        if(published!=null && List.of("ACTIVE","DELETED").contains(published.status)) return new AiExecutionHandlerResult("AI_VIDEO_RESULT",published.id);
        var source=results.selectActive(task.tenantId,task.projectId,task.sourceResultId);
        service.requireUsableSource(source,task.tenantId,task.projectId);
        if(task.deadlineAt.isBefore(LocalDateTime.now())) throw new ProviderFailure("字幕擦除处理超时，可在任务中心重试。");
        if(task.providerTaskId==null) {
            phase(context,"ERASE_SUBMIT",10);
            // Resolve at provider submission time, not when the job was queued.
            String input=task.inputUrl!=null?task.inputUrl:playback.issueForProcessing(task.tenantId,task.projectId,source.id,task.createdBy).url();
            // Freeze the request before I/O so an unknown submit can replay the same client_token and body.
            if(task.inputUrl==null) owned(context,()->{task.inputUrl=input;task.updatedAt=LocalDateTime.now();tasks.updateById(task);return null;});
            SubtitleEraseRequest.Region region=null;
            try { if(task.regionJson!=null) region=json.readValue(task.regionJson,SubtitleEraseRequest.Region.class); }
            catch(Exception exception){throw new ProviderFailure("擦除区域记录无法读取，请重新发起任务。");}
            String remote=client.submit(input,new SubtitleEraseRequest(task.method,task.quality,region),
                "subtitle-erase-"+task.id+"-"+task.generation);
            owned(context,()->{task.providerTaskId=remote;task.inputUrl=input;task.updatedAt=LocalDateTime.now();tasks.updateById(task);service.invalidate(task);return null;});
            phase(context,"ERASE_PROCESSING",25);
            throw new AiExecutionDeferredException("字幕擦除处理中，稍后查询结果。");
        }
        if(task.outputUrl==null) {
            phase(context,"ERASE_PROCESSING",35);
            var remote=client.poll(task.providerTaskId);
            if("running".equals(remote.status())) throw new AiExecutionDeferredException("字幕擦除处理中，稍后查询结果。");
            if("failed".equals(remote.status())) {
                owned(context,()->{tasks.update(null,new UpdateWrapper<SubtitleEraseTaskEntity>().set("provider_failed",true)
                    .set("updated_at",LocalDateTime.now()).eq("id",task.id));return null;});
                throw new ProviderFailure(remote.error());
            }
            owned(context,()->{
                task.outputUrl=remote.videoUrl();task.outputExpiresAt=LocalDateTime.ofInstant(remote.expiresAt(),ZoneOffset.UTC);
                task.outputDuration=remote.duration();task.outputResolution=remote.resolution();task.updatedAt=LocalDateTime.now();
                tasks.updateById(task);return null;
            });
        }
        phase(context,"ERASE_PUBLISH",80);
        AiVideoResultEntity result=owned(context,()->{
            if(task.resultId!=null) {
                var existing=results.selectById(task.resultId);
                existing.sourceVideoUrl=task.outputUrl;existing.sourceVideoExpiresAt=task.outputExpiresAt;
                existing.durationSeconds=task.outputDuration==null?source.durationSeconds:task.outputDuration;
                results.updateById(existing);return existing;
            }
            var video=new AiVideoResultEntity();video.tenantId=task.tenantId;video.projectId=task.projectId;
            video.taskId=source.taskId;video.storyboardId=task.storyboardId;video.executionId=task.executionId;
            video.processingType="SUBTITLE_ERASE";video.sourceResultId=source.id;video.processingMethod=task.method;video.processingQuality=task.quality;
            video.sourceVideoUrl=task.outputUrl;video.sourceVideoExpiresAt=task.outputExpiresAt;
            video.storagePath=keys.projectOriginal(task.tenantId,task.projectId,"videos",task.id,"subtitle-erase",task.createdAt.toLocalDate(),"mp4");
            video.videoUrl=files.publicUrl(video.storagePath);video.durationSeconds=task.outputDuration==null?source.durationSeconds:task.outputDuration;
            video.width=source.width;video.height=source.height;video.format="mp4";video.isSelected=false;video.status="PROCESSING";
            if(task.outputResolution!=null && task.outputResolution.matches("(480|540|720|1080)p") && source.width!=null && source.height!=null) {
                int shortSide=Integer.parseInt(task.outputResolution.replace("p",""));
                double scale=(double)shortSide/Math.min(source.width,source.height);
                video.width=(int)Math.round(source.width*scale/2)*2;video.height=(int)Math.round(source.height*scale/2)*2;
            }
            video.createdAt=LocalDateTime.now();video.updatedAt=video.createdAt;results.insert(video);
            task.resultId=video.id;task.updatedAt=LocalDateTime.now();tasks.updateById(task);return video;
        });
        if(result.fileSize==null || result.fileSize<=0) {
            if(task.outputExpiresAt==null || !task.outputExpiresAt.toInstant(ZoneOffset.UTC).isAfter(Instant.now())) {
                owned(context,()->{tasks.update(null,new UpdateWrapper<SubtitleEraseTaskEntity>().set("provider_failed",true).eq("id",task.id));return null;});
                throw new ProviderFailure("擦除结果已过期，请重试生成新的擦除结果。");
            }
            Path file=null;
            try {
                file=Files.createTempFile("ant-short-tv-subtitle-", ".mp4");
                long size=client.downloadTo(task.outputUrl,file);storage.uploadFile(result.storagePath,file,"video/mp4");
                owned(context,()->{result.fileSize=size;result.updatedAt=LocalDateTime.now();results.updateById(result);return null;});
            } catch(java.io.IOException exception) {throw new IllegalStateException("擦除结果转存失败，可在任务中心重试。");}
            finally {if(file!=null)try{Files.deleteIfExists(file);}catch(java.io.IOException ignored){}}
        }
        var cover=covers.prepareProcessed(videos.selectById(source.taskId),result);
        owned(context,()->{
            service.requireUsableSource(results.selectActive(task.tenantId,task.projectId,source.id),task.tenantId,task.projectId);
            result.status="ACTIVE";result.isSelected=false;result.updatedAt=LocalDateTime.now();results.updateById(result);covers.publish(cover);
            task.updatedAt=LocalDateTime.now();tasks.updateById(task);service.invalidate(task);return null;
        });
        return new AiExecutionHandlerResult("AI_VIDEO_RESULT",result.id);
    }
    private void phase(AiExecutionContext context,String phase,int progress) {
        owned(context,()->{executions.update(null,new UpdateWrapper<AiExecutionTaskEntity>().set("phase",phase).set("progress",progress)
            .set("updated_at",LocalDateTime.now()).eq("id",context.task().id));return null;});
    }
    private <T> T owned(AiExecutionContext context,Supplier<T> work) {
        return tx.execute(status->{
            if(context.claim()==null || executions.lockActiveClaim(context.task().id,context.claim().claimToken())==null)
                throw new AiExecutionClaimLostException(context.task().id);
            return work.get();
        });
    }
    private static class ProviderFailure extends RuntimeException { ProviderFailure(String message){super(message);} }
}
