package com.antshorttv.freecreation;

import com.antshorttv.common.*;
import com.antshorttv.execution.*;
import com.antshorttv.security.TenantContext;
import com.antshorttv.storage.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qcloud.cos.COS;
import com.qcloud.cos.model.ciModel.mediaInfo.MediaInfoRequest;
import com.qcloud.cos.model.ciModel.common.MediaInputObject;
import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Only accepts verified uploads or media already owned by a visible free creation. */
@Service
public class FreeCreationMediaService {
    private final FreeCreationMediaMapper media;
    private final FreeCreationMapper creations;
    private final MediaUploadSessionService uploads;
    private final ObjectStorageService storage;
    private final ObjectStorageKeyFactory keys;
    private final ImageDisplayRenditionService images;
    private final CloudInfiniteVideoCoverService covers;
    private final MediaDeliveryGrantService grants;
    private final COS cos;
    private final ObjectStorageProperties properties;
    private final JdbcTemplate jdbc;
    private final AiExecutionService executions;
    private final org.springframework.transaction.support.TransactionTemplate transactions;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    // Completed uploads are promoted to immutable original keys. Cache metadata, never authorization or prices.
    private record UploadIdentity(Long tenant, String key, String etag, String type, long size) {}
    private record Metadata(Integer width,Integer height,BigDecimal duration,BigDecimal fps,String format,long loadedAt) {}
    private final Map<UploadIdentity,Metadata> uploadMetadata=Collections.synchronizedMap(
        new LinkedHashMap<UploadIdentity,Metadata>(32,0.75f,true) {
            @Override protected boolean removeEldestEntry(Map.Entry<UploadIdentity,Metadata> eldest) {return size()>256;}
        });
    private final Object[] probeLocks=java.util.stream.IntStream.range(0,32).mapToObj(ignored->new Object()).toArray();

    public FreeCreationMediaService(FreeCreationMediaMapper media, FreeCreationMapper creations,
        MediaUploadSessionService uploads, ObjectStorageService storage, ObjectStorageKeyFactory keys,
        ImageDisplayRenditionService images, MediaDeliveryGrantService grants, COS cos,
        ObjectStorageProperties properties, JdbcTemplate jdbc, CloudInfiniteVideoCoverService covers,
        AiExecutionService executions,org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.media=media; this.creations=creations; this.uploads=uploads; this.storage=storage;
        this.executions=executions;this.transactions=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        this.covers=covers; this.keys=keys; this.images=images; this.grants=grants; this.cos=cos; this.properties=properties; this.jdbc=jdbc;
    }

    public FreeCreationMediaEntity reference(TenantContext context, FreeCreationRequest.Reference request) {
        return reference(context, request, true);
    }

    /** Image pricing uses reference count, not pixel dimensions. Ownership and completed-upload checks still run. */
    public FreeCreationMediaEntity referenceForImageEstimate(TenantContext context, FreeCreationRequest.Reference request) {
        if (!"IMAGE".equals(request.kind())) throw invalid("图片生成仅支持图片参考素材。");
        return reference(context, request, false);
    }

    private FreeCreationMediaEntity reference(TenantContext context, FreeCreationRequest.Reference request, boolean inspectMetadata) {
        boolean token=request.uploadSessionToken()!=null && !request.uploadSessionToken().isBlank();
        if (token == (request.referenceId()!=null)) throw invalid("参考素材必须指定上传会话或已授权素材之一。");
        FreeCreationMediaEntity result;
        if (token) {
            VerifiedMediaUpload verified;
            try { verified=uploads.requireCompleted(context.userId(),context.tenantId(),null,request.uploadSessionToken()); }
            catch (IllegalArgumentException ex) { throw invalid("参考素材上传会话不可用或不属于当前用户及团队。"); }
            if (!verified.contentType().toUpperCase(Locale.ROOT).startsWith(request.kind()+"/"))
                throw invalid("参考素材类型与已验证文件不一致。");
            result=new FreeCreationMediaEntity(); result.tenantId=context.tenantId(); result.kind=request.kind();
            result.objectKey=verified.objectKey(); result.mimeType=verified.contentType(); result.fileSize=verified.size();
            if (inspectMetadata) result.fileName=jdbc.queryForObject("select file_name from media_upload_session where session_token=?",String.class,request.uploadSessionToken());
            result.format=format(result.mimeType);
            if (inspectMetadata) probeUpload(result,verified);
        } else {
            result=media.selectById(request.referenceId());
            FreeCreationEntity owner=result==null?null:creations.selectById(result.creationId);
            if (result==null || owner==null || !context.tenantId().equals(owner.tenantId)
                || !context.tenantId().equals(result.tenantId) || !request.kind().equals(result.kind))
                throw new BusinessException(ErrorCode.NOT_FOUND,"参考素材不存在。");
            if ("OUTPUT".equals(result.role)) {
                String state=jdbc.queryForObject("select status from ai_execution_task where id=?",String.class,owner.executionId);
                if (!"SUCCEEDED".equals(state)) throw invalid("仅已完成作品可用作参考素材。");
            }
            // MyBatis may return the same instance again in this read transaction.
            result=copyReference(result);
        }
        if (inspectMetadata && "OUTPUT".equals(result.role) && "IMAGE".equals(result.kind)) {
            var display=images.display(identity(result));
            result.thumbnailKey=display!=null && "READY".equals(display.status())?display.objectKey():result.objectKey;
        }
        result.id=null; result.creationId=null; result.role="REFERENCE"; result.createdAt=LocalDateTime.now();
        return result;
    }

    private static FreeCreationMediaEntity copyReference(FreeCreationMediaEntity source) {
        var copy=new FreeCreationMediaEntity();
        copy.id=source.id;copy.tenantId=source.tenantId;copy.creationId=source.creationId;copy.role=source.role;copy.position=source.position;
        copy.kind=source.kind;copy.fileName=source.fileName;copy.objectKey=source.objectKey;copy.thumbnailKey=source.thumbnailKey;
        copy.mimeType=source.mimeType;copy.format=source.format;copy.fileSize=source.fileSize;
        copy.width=source.width;copy.height=source.height;copy.durationSeconds=source.durationSeconds;copy.fps=source.fps;
        return copy;
    }

    private void probe(FreeCreationMediaEntity item) {
        try {
            if ("IMAGE".equals(item.kind)) {
                try (var stream=storage.resource(item.objectKey).getInputStream(); var input=ImageIO.createImageInputStream(stream)) {
                    var readers=ImageIO.getImageReaders(input);
                    if (!readers.hasNext()) throw invalid("参考图片格式无法识别。");
                    var reader=readers.next();
                    try { reader.setInput(input); item.width=reader.getWidth(0); item.height=reader.getHeight(0);
                        String actual=reader.getFormatName().toLowerCase(Locale.ROOT);
                        if("jpg".equals(actual)) actual="jpeg";
                        if(!actual.equals(item.format)) throw invalid("参考图片实际格式与上传类型不一致。");
                        item.format=actual;
                    }
                    finally { reader.dispose(); }
                }
            } else {
                MediaInfoRequest request=new MediaInfoRequest(); request.setBucketName(properties.getBucket());
                MediaInputObject input=new MediaInputObject(); input.setObject(item.objectKey); request.setInput(input);
                var info=cos.generateMediainfo(request).getMediaInfo();
                if ("VIDEO".equals(item.kind)) {
                    var video=info.getStream().getMediaInfoVideoList().get(0);
                    item.width=Integer.valueOf(video.getWidth()); item.height=Integer.valueOf(video.getHeight());
                    item.durationSeconds=new BigDecimal(video.getDuration()); item.fps=decimalRate(video.getFps());
                } else item.durationSeconds=new BigDecimal(info.getStream().getMediaInfoAudioList().get(0).getDuration());
            }
        } catch (BusinessException ex) { throw ex; }
        catch (Exception ex) { throw invalid("无法读取参考素材元数据，请检查文件格式后重新上传。"); }
    }

    private void probeUpload(FreeCreationMediaEntity item,VerifiedMediaUpload upload) {
        if(upload.eTag()==null || upload.eTag().isBlank()) {probe(item);return;}
        var identity=new UploadIdentity(item.tenantId,upload.objectKey(),upload.eTag(),upload.contentType(),upload.size());
        synchronized(probeLocks[Math.floorMod(identity.hashCode(),probeLocks.length)]) {
            var metadata=uploadMetadata.get(identity);
            if(metadata==null || System.nanoTime()-metadata.loadedAt()>=Duration.ofMinutes(10).toNanos()) {
                probe(item);
                metadata=new Metadata(item.width,item.height,item.durationSeconds,item.fps,item.format,System.nanoTime());
                uploadMetadata.put(identity,metadata);
            }
            item.width=metadata.width();item.height=metadata.height();item.durationSeconds=metadata.duration();
            item.fps=metadata.fps();item.format=metadata.format();
        }
    }

    static BigDecimal decimalRate(String value) {
        String[] parts=value.split("/");
        return parts.length==2?new BigDecimal(parts[0]).divide(new BigDecimal(parts[1]),4,java.math.RoundingMode.HALF_UP):new BigDecimal(value);
    }

    public List<FreeCreationMediaEntity> items(Long creationId,String role) {
        return media.selectList(new QueryWrapper<FreeCreationMediaEntity>().eq("creation_id",creationId).eq("role",role).orderByAsc("position"));
    }

    public void attach(Long creationId,List<FreeCreationMediaEntity> references) {
        for(int i=0;i<references.size();i++) { var ref=references.get(i); ref.creationId=creationId; ref.position=i; media.insert(ref); }
    }

    public FreeCreationMediaEntity store(FreeCreationEntity creation,int index,String url,BigDecimal duration,AiExecutionContext context) {
        var prior=media.selectOne(new QueryWrapper<FreeCreationMediaEntity>().eq("creation_id",creation.id).eq("role","OUTPUT").eq("position",index));
        if(prior!=null) { completeRenditions(creation,prior,context); return prior; }
        Path file=null;
        try {
            boolean image="IMAGE".equals(creation.mediaType);
            file=Files.createTempFile("free-creation-",image?".image":".mp4");
            if(image && url.startsWith("data:image/")) {
                int comma=url.indexOf(',');
                byte[] bytes=Base64.getDecoder().decode(url.substring(comma+1));
                if(bytes.length>64*1024*1024) throw invalid("生成图片超过文件大小限制。");
                Files.write(file,bytes);
            } else {
                URI uri=URI.create(url);
                if(!Set.of("https","http").contains(uri.getScheme())) throw invalid("供应商生成文件地址无效。");
                var response=http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
                try(InputStream input=response.body(); OutputStream output=Files.newOutputStream(file)) {
                    if(response.statusCode()<200 || response.statusCode()>=300) throw invalid("生成文件暂时无法下载。");
                    long total=0; byte[] buffer=new byte[65536]; int read;
                    while((read=input.read(buffer))!=-1) { total+=read; if(total>(image?64L:512L)*1024*1024) throw invalid("生成文件超过大小限制。"); output.write(buffer,0,read); }
                }
            }
            FreeCreationMediaEntity item=new FreeCreationMediaEntity(); item.tenantId=creation.tenantId; item.creationId=creation.id;
            item.role="OUTPUT"; item.position=index; item.kind=creation.mediaType; item.durationSeconds=duration;
            item.mimeType="video/mp4"; item.format="mp4";
            if(image) {
                try(var input=ImageIO.createImageInputStream(file.toFile())) {
                    var readers=ImageIO.getImageReaders(input); if(!readers.hasNext()) throw invalid("供应商未生成有效图片。");
                    var reader=readers.next();
                    try { reader.setInput(input); item.width=reader.getWidth(0); item.height=reader.getHeight(0);
                        item.format=reader.getFormatName().toLowerCase(Locale.ROOT); if("jpg".equals(item.format)) item.format="jpeg";
                        item.mimeType="image/"+item.format;
                    } finally { reader.dispose(); }
                }
            }
            item.fileName="free-creation-"+creation.id+"-"+(index+1)+"."+item.format;
            item.objectKey=keys.tenantOriginal(creation.tenantId,"free-creations",creation.id,"output-"+index,creation.createdAt.toLocalDate(),item.format);
            StoredObject stored;
            try { stored=storage.metadata(item.objectKey); }
            catch(BusinessException missing) { stored=storage.uploadOriginal(item.objectKey,file,item.mimeType); }
            if(stored.size()!=Files.size(file) || !item.mimeType.equals(stored.contentType())) throw invalid("已保存作品与供应商结果不一致。");
            item.fileSize=stored.size(); item.createdAt=LocalDateTime.now();
            owned(context,()->media.insert(item));
            completeRenditions(creation,item,context); return item;
        } catch(BusinessException ex) { throw ex; }
        catch(Exception ex) { throw new IllegalStateException("生成文件保存失败。",ex); }
        finally { if(file!=null) try { Files.deleteIfExists(file); } catch(IOException ignored) {} }
    }

    private void owned(AiExecutionContext context,Runnable work) {
        transactions.executeWithoutResult(transaction->{
            var task=executions.requireTaskForUpdate(context.task().id);
            if(!"RUNNING".equals(task.status) || !Objects.equals(task.claimToken,context.claim().claimToken())) throw new AiExecutionClaimLostException(task.id);
            work.run();
        });
    }

    private void completeRenditions(FreeCreationEntity creation,FreeCreationMediaEntity item,AiExecutionContext context) {
        owned(context,()->{});
        if("IMAGE".equals(item.kind)) {
            var original=images.originalDetails(identity(item));
            var display=images.display(identity(item));
            if(original==null) {
                var registered=images.registerOriginalAndSubmit(identity(item),storage.metadata(item.objectKey),item.width,item.height,"free-creation:"+creation.id+":"+item.position);
                item.thumbnailKey=registered.displayKey();
            } else if(display==null || "FAILED".equals(display.status())) {
                item.thumbnailKey=images.retryFailedDisplay(identity(item),"free-creation:"+creation.id+":"+item.position).displayKey();
            } else item.thumbnailKey=display.objectKey();
        } else {
            if(item.width==null || item.fps==null) probe(item);
            String coverKey=keys.tenantOriginal(creation.tenantId,"free-creation-covers",creation.id,"output-"+item.position,creation.createdAt.toLocalDate(),"jpg");
            item.thumbnailKey=covers.create(coverIdentity(item),item.objectKey,coverKey).originalKey();
        }
        owned(context,()->media.updateById(item));
    }

    public Map<String,Object> present(TenantContext context,FreeCreationMediaEntity item) {
        Map<String,Object> result=new LinkedHashMap<>(); result.put("id",item.id); result.put("referenceId",item.id);
        result.put("kind",item.kind); result.put("fileName",item.fileName); result.put("durationSeconds",item.durationSeconds);
        result.put("previewUrl",grant(context,item,"IMAGE".equals(item.kind))); result.put("thumbnailUrl",!"AUDIO".equals(item.kind) && ("IMAGE".equals(item.kind) || item.thumbnailKey!=null)?grant(context,item,true):null);
        result.put("downloadUrl","/api/free-creations/"+item.creationId+"/outputs/"+item.id+"/download"); return result;
    }

    public String grant(TenantContext context,FreeCreationMediaEntity item,boolean thumbnail) {
        if(!context.tenantId().equals(item.tenantId)) throw new BusinessException(ErrorCode.NOT_FOUND,"素材不存在。");
        String key=item.objectKey;
        if(thumbnail && item.thumbnailKey!=null) {
            if("OUTPUT".equals(item.role)) {
                if("VIDEO".equals(item.kind)) key=item.thumbnailKey;
                var display=images.display("VIDEO".equals(item.kind)?coverIdentity(item):identity(item));
                if(display!=null && "READY".equals(display.status())) key=display.objectKey();
            } else key=item.thumbnailKey;
        }
        return grants.issue(new DeliveryGrantRequest(context.tenantId(),null,context.userId(),"FREE_CREATION_MEDIA",item.id,
            "v1",thumbnail?"THUMBNAIL":"ORIGINAL",key,"VIDEO".equals(item.kind))).url();
    }

    private MediaObjectIdentity coverIdentity(FreeCreationMediaEntity item) {
        return new MediaObjectIdentity(item.tenantId,null,"FREE_CREATION_VIDEO_COVER",item.creationId,"output-"+item.position);
    }
    private MediaObjectIdentity identity(FreeCreationMediaEntity item) {
        return new MediaObjectIdentity(item.tenantId,null,"FREE_CREATION_OUTPUT",item.creationId,"output-"+item.position);
    }
    private static String format(String mime) {
        return switch(mime.toLowerCase(Locale.ROOT)) { case "image/jpeg" -> "jpeg"; case "audio/mpeg" -> "mp3";
            case "audio/x-wav" -> "wav"; case "audio/mp4" -> "m4a"; case "video/quicktime" -> "mov";
            default -> mime.substring(mime.indexOf('/')+1); };
    }
    private static BusinessException invalid(String message) { return new BusinessException(ErrorCode.VALIDATION_ERROR,message); }
}
