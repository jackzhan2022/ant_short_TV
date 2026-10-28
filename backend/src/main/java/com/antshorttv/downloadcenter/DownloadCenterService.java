package com.antshorttv.downloadcenter;

import com.antshorttv.authsession.AuthenticatedUser;
import com.antshorttv.common.*;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.storage.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class DownloadCenterService {
    private static final Set<String> STATUSES=Set.of("QUEUED","RUNNING","READY","FAILED","EXPIRED");
    private static final String STATUS="case when status='READY' and expires_at<=? then 'EXPIRED' else status end";
    private final JdbcTemplate jdbc;
    private final TenantContextResolver tenants;
    private final DownloadSourceResolver sources;
    private final DownloadArchiveService archives;
    private final ObjectStorageService storage;
    private final ObjectMapper json;
    private final Clock clock;
    @Autowired public DownloadCenterService(JdbcTemplate jdbc,TenantContextResolver tenants,DownloadSourceResolver sources,
        DownloadArchiveService archives,ObjectStorageService storage,ObjectMapper json) {
        this(jdbc,tenants,sources,archives,storage,json,Clock.systemUTC());
    }
    DownloadCenterService(JdbcTemplate jdbc,TenantContextResolver tenants,DownloadSourceResolver sources,
        DownloadArchiveService archives,ObjectStorageService storage,ObjectMapper json,Clock clock) {
        this.jdbc=jdbc;this.tenants=tenants;this.sources=sources;this.archives=archives;this.storage=storage;this.json=json;this.clock=clock;
    }
    public DownloadTask create(Long tenant,DownloadTask.Request request) {
        var context=tenants.requireActiveMember(tenant);
        if(request==null||request.name()==null||request.name().isBlank()||request.name().length()>160)throw invalid("请填写不超过 160 字的任务名称。");
        var normalized=sources.normalize(tenant,request.sources());var files=sources.resolve(tenant,normalized);
        String id=UUID.randomUUID().toString();String name=request.name().strip();
        var saved=new DownloadTask.Request(name,normalized);
        boolean single=files.size()==1;
        String bundle=sources.bundleName(tenant,normalized,name);
        if(bundle==null || bundle.isBlank())bundle=name;
        String stamp=java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.of("Asia/Shanghai")).format(clock.instant());
        String fileName=DownloadArchiveService.uniqueName(single?files.get(0).name():DownloadFileNames.stem(bundle)+"_"+stamp+"_"+id.substring(0,8)+".zip",new HashSet<>());
        jdbc.update("insert into download_task(id,tenant_id,user_id,name,source_type,request_json,status,file_count,created_at,output_format,file_name,content_type) values(?,?,?,?,?,?,'QUEUED',?,?,?,?,?)",
            id,tenant,context.userId(),name,normalized.get(0).kind(),encode(saved),files.size(),now(),single?"FILE":"ZIP",fileName,single?files.get(0).contentType():"application/zip");
        return detail(tenant,id);
    }
    public DownloadTask.Page list(Long tenant,String status,String keyword,int page,int pageSize) {
        var context=tenants.requireActiveMember(tenant);
        if(status!=null&&!status.isBlank()&&!STATUSES.contains(status))throw invalid("任务状态不正确。");
        if(page<1||pageSize<1||pageSize>100||page>100000)throw invalid("分页参数不正确。");
        if(keyword!=null&&keyword.length()>160)throw invalid("搜索内容过长。");
        List<Object> args=new ArrayList<>(List.of(tenant,context.userId()));String where=" where tenant_id=? and user_id=?";
        if(status!=null&&!status.isBlank()){where+=" and "+STATUS+"=?";args.add(now());args.add(status);}
        if(keyword!=null&&!keyword.isBlank()){where+=" and lower(name) like ? escape '!'";args.add("%"+keyword.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");}
        long count=jdbc.queryForObject("select count(*) from download_task"+where,Long.class,args.toArray());
        args.add(pageSize);args.add((page-1)*pageSize);
        return new DownloadTask.Page(jdbc.query("select * from download_task"+where+" order by created_at desc,id desc limit ? offset ?",(row,index)->present(row),args.toArray()),count,page,pageSize);
    }
    public DownloadTask detail(Long tenant,String id) {return owned(tenant,id).task();}
    public DownloadTask retry(Long tenant,String id) {
        var row=owned(tenant,id);
        if(!Set.of("FAILED","EXPIRED").contains(row.task().status()))throw invalid("仅失败或过期任务可以重新生成。");
        return create(tenant,row.request());
    }
    public record Delivery(String fileName,String contentType,String outputFormat,long fileSize,Instant expiresAt,
        String url,String cacheKey,String eTag,String sourceUrl) {}
    public DownloadTask prepare(Long tenant,String id) {return authorizedDownload(tenant,id).task();}
    public String download(Long tenant,String id) {
        var row=authorizedDownload(tenant,id);return signed(row);
    }
    private String signed(Row row) {
        if(!row.task().expiresAt().isAfter(clock.instant()))throw invalid("文件已过期，请重新生成。");
        Instant limit=clock.instant().plusSeconds(60);
        if(row.task().expiresAt().isBefore(limit))limit=row.task().expiresAt();
        return storage.downloadUrl(key(row),row.task().fileName(),limit);
    }
    public Delivery delivery(Long tenant,String id) {
        var row=authorizedDownload(tenant,id);var task=row.task();String cacheKey=null,eTag=null,sourceUrl=null;
        if("FILE".equals(task.outputFormat()) && task.fileSize()<=64L*1024*1024) {
            var snapshot=storage.metadata(key(row));eTag=snapshot.eTag();
            var file=sources.resolve(tenant,row.request().sources()).get(0);
            String identity=key(row);
            if(file.objectKey()!=null) {
                try {
                    var original=storage.metadata(file.objectKey());
                    if(eTag!=null && !eTag.isBlank() && Objects.equals(original.eTag(),eTag) && original.size()==snapshot.size()) {
                        identity=file.objectKey();sourceUrl=storage.publicUrl(file.objectKey());
                    }
                }catch(BusinessException ignored) {
                    // Optional preview-cache lookup must not prevent delivery of the valid snapshot.
                }
            }
            try {
                String digest=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((identity+"\n"+eTag).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                cacheKey="/__download-cache/"+row.tenant()+"/"+row.user()+"/"+digest;
            }catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
        }
        return new Delivery(task.fileName(),task.contentType(),task.outputFormat(),task.fileSize(),task.expiresAt(),signed(row),cacheKey,eTag,sourceUrl);
    }
    private Row authorizedDownload(Long tenant,String id) {
        var row=owned(tenant,id);
        if(!row.task().status().equals("READY"))throw invalid("文件尚未准备完成或已过期，请重新生成。");
        sources.resolve(tenant,row.request().sources());
        // Re-check after authorization; expiry must not depend on the cleanup scheduler.
        if(!row.task().expiresAt().isAfter(clock.instant()))throw invalid("文件已过期，请重新生成。");
        return row;
    }
    public List<String> queued(int limit) {
        return jdbc.queryForList("select id from download_task where status='QUEUED' order by created_at,id limit ?",String.class,limit);
    }
    public boolean claim(String id) {
        return jdbc.update("update download_task set status='RUNNING',lease_until=? where id=? and status='QUEUED'",timestamp(clock.instant().plusSeconds(300)),id)==1;
    }
    public void heartbeat(String id) {
        if(jdbc.update("update download_task set lease_until=? where id=? and status='RUNNING' and lease_until>?",
            timestamp(clock.instant().plusSeconds(300)),id,now())!=1)throw new IllegalStateException("下载任务执行已中断");
    }
    public void generate(String id) {
        var row=load(id);if(!row.task().status().equals("RUNNING"))return;
        var previous=SecurityContextHolder.getContext();var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(row.user(),"", "download:"+id,
            LocalDateTime.ofInstant(clock.instant().plusSeconds(7200),ZoneOffset.UTC)),null,List.of()));
        SecurityContextHolder.setContext(context);
        try {
            tenants.requireActiveMember(row.tenant());heartbeat(id);
            var files=sources.resolve(row.tenant(),row.request().sources());
            long size=archives.generate(key(row),files,()->heartbeat(id));
            Instant completed=clock.instant();
            int saved=jdbc.update("update download_task set status='READY',file_size=?,completed_at=?,expires_at=?,lease_until=null where id=? and status='RUNNING' and lease_until>?",
                size,timestamp(completed),timestamp(completed.plus(Duration.ofHours(24))),id,timestamp(completed));
            if(saved!=1)deleteArchive(row);
        } catch(Exception ex) {
            String message=ex instanceof BusinessException?ex.getMessage():"文件生成失败，请重新生成。";
            jdbc.update("update download_task set status='FAILED',error_message=?,lease_until=null where id=? and status='RUNNING'",
                message.substring(0,Math.min(500,message.length())),id);
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Download task {} failed",id,ex);
            deleteArchive(row);
        } finally {SecurityContextHolder.setContext(previous);}
    }
    public void cleanup() {
        jdbc.update("update download_task set status='EXPIRED' where status='READY' and expires_at<=?",now());
        jdbc.update("update download_task set status='FAILED',error_message='生成任务中断，请重新生成。',lease_until=null where status='RUNNING' and lease_until<=?",now());
        var rows=jdbc.query("select * from download_task where status in ('EXPIRED','FAILED') and file_deleted_at is null order by created_at limit 100",(rs,index)->row(rs));
        rows.forEach(this::deleteArchive);
    }
    private void deleteArchive(Row row) {
        try {
            storage.delete(key(row));
            archives.deleteTemporary(row.task().id());
            jdbc.update("update download_task set file_deleted_at=? where id=? and status in ('EXPIRED','FAILED')",now(),row.task().id());
        }catch(Exception ex){
            jdbc.update("update download_task set file_deleted_at=null where id=?",row.task().id());
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Download archive cleanup will retry for {}",row.task().id(),ex);
        }
    }
    private Row owned(Long tenant,String id) {
        var context=tenants.requireActiveMember(tenant);
        var rows=jdbc.query("select * from download_task where id=? and tenant_id=? and user_id=?",(rs,index)->row(rs),id,tenant,context.userId());
        if(rows.isEmpty())throw new BusinessException(ErrorCode.NOT_FOUND,"下载任务不存在。");return rows.get(0);
    }
    private Row load(String id){return jdbc.queryForObject("select * from download_task where id=?",(rs,index)->row(rs),id);}
    private Row row(ResultSet row)throws SQLException {
        try{return new Row(row.getLong("tenant_id"),row.getLong("user_id"),present(row),json.readValue(row.getString("request_json"),DownloadTask.Request.class));}
        catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException("Invalid saved download task",ex);}
    }
    private DownloadTask present(ResultSet row)throws SQLException {
        Instant expires=instant(row,"expires_at");String status=row.getString("status");
        if(status.equals("READY")&&expires!=null&&!expires.isAfter(clock.instant()))status="EXPIRED";
        return new DownloadTask(row.getString("id"),row.getString("name"),row.getString("source_type"),status,row.getInt("file_count"),row.getObject("file_size",Long.class),row.getString("error_message"),instant(row,"created_at"),instant(row,"completed_at"),expires,
            Objects.toString(row.getString("output_format"),"ZIP"),
            Objects.toString(row.getString("file_name"),DownloadArchiveService.uniqueName(DownloadFileNames.stem(row.getString("name"))+".zip",new HashSet<>())),
            Objects.toString(row.getString("content_type"),"application/zip"));
    }
    private static Instant instant(ResultSet row,String column)throws SQLException{var value=row.getTimestamp(column);return value==null?null:value.toInstant();}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException(ex);}}
    private record Row(Long tenant,Long user,DownloadTask task,DownloadTask.Request request) {}
    private String key(Row row){return "download-archives/"+row.tenant()+"/"+row.user()+"/"+row.task().id()+("FILE".equals(row.task().outputFormat())?".file":".zip");}
    private Timestamp now(){return timestamp(clock.instant());}
    private static Timestamp timestamp(Instant value){return Timestamp.from(value);}
    private static BusinessException invalid(String message){return new BusinessException(ErrorCode.VALIDATION_ERROR,message);}
}
