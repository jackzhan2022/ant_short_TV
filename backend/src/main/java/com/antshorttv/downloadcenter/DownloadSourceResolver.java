package com.antshorttv.downloadcenter;

import com.antshorttv.aiimage.AiImageDownloadService;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.freecreation.FreeCreationService;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.review.ReviewReadService;
import com.antshorttv.review.ReviewWorkbenchService;
import com.antshorttv.storage.ObjectStorageService;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DownloadSourceResolver {
    @FunctionalInterface public interface Content { InputStream open() throws IOException; }
    public record File(String name, Content content) {}
    private final JdbcTemplate jdbc;
    private final ProjectPermissionGuard permissions;
    private final AiImageDownloadService images;
    private final FreeCreationService creations;
    private final ReviewReadService reviews;
    private final ReviewWorkbenchService projectReviews;
    private final ObjectStorageService storage;
    private final ObjectStorageKeyFactory keys;
    public DownloadSourceResolver(JdbcTemplate jdbc, ProjectPermissionGuard permissions, AiImageDownloadService images,
        FreeCreationService creations, ReviewReadService reviews, ReviewWorkbenchService projectReviews, ObjectStorageService storage, ObjectStorageKeyFactory keys) {
        this.jdbc=jdbc;this.permissions=permissions;this.images=images;this.creations=creations;this.reviews=reviews;this.projectReviews=projectReviews;this.storage=storage;this.keys=keys;
    }
    public List<DownloadTask.Source> normalize(Long tenant, List<DownloadTask.Source> requested) {
        if(requested==null || requested.isEmpty() || requested.size()>1000) throw invalid("请选择 1 至 1000 个文件。");
        List<DownloadTask.Source> result=new ArrayList<>();
        for(var source:requested) {
            if(source==null || source.kind()==null || source.id()==null || source.id()<1) throw invalid("下载来源不正确。");
            if(Set.of("STORYBOARD_VIDEO","EPISODE_STORYBOARDS").contains(source.kind())) {
                requireProject(tenant,source.projectId(),"PROJECT:VIEW");
                String selector=source.kind().equals("STORYBOARD_VIDEO")?"s.id":"s.episode_no";
                result.addAll(jdbc.query("""
                    select s.current_shot_video_url,c.id composed_id,r.id video_id
                    from storyboard s
                    left join shot_compose_result c on c.id=s.current_shot_result_id and c.tenant_id=s.tenant_id and c.project_id=s.project_id and c.storyboard_id=s.id and c.status='ACTIVE'
                    left join ai_video_result r on r.id=s.current_video_result_id and r.tenant_id=s.tenant_id and r.project_id=s.project_id and r.storyboard_id=s.id and r.status='ACTIVE'
                    where s.tenant_id=? and s.project_id=? and s.deleted_at is null and
                    """+selector+"=? order by s.shot_no,s.id limit 1001",(row,index)->{
                        boolean composed=row.getString("current_shot_video_url")!=null&&!row.getString("current_shot_video_url").isBlank();
                        Long id=row.getObject(composed?"composed_id":"video_id",Long.class);
                        return id==null?null:new DownloadTask.Source(composed?"COMPOSE":"VIDEO",source.projectId(),id,null);
                    },tenant,source.projectId(),source.id()).stream().filter(Objects::nonNull).toList());
            } else result.add(source);
        }
        var unique=result.stream().distinct().toList();
        if(unique.isEmpty() || unique.size()>1000) throw invalid("暂无可下载文件，或文件数量超过 1000 个。");
        return unique;
    }
    // Validation resolves metadata only. Opening private object streams is deferred to the background worker.
    public List<File> resolve(Long tenant,List<DownloadTask.Source> sources) {
        return sources.stream().map(source->resolveOne(tenant,source)).toList();
    }
    private File resolveOne(Long tenant,DownloadTask.Source source) {
        if(source.kind().equals("FREE_CREATION") || source.kind().equals("FREE_REFERENCE")) {
            if(source.parentId()==null)throw invalid("缺少创作记录。");
            var file=source.kind().equals("FREE_CREATION")?creations.describeDownload(tenant,source.parentId(),source.id())
                :creations.describeReference(tenant,source.parentId(),source.id());
            return stored(file.fileName(),file.objectKey());
        }
        if(source.kind().equals("REVIEW") || source.kind().equals("PROJECT_REVIEW")) {
            String text=source.kind().equals("REVIEW")?reviews.report(tenant,source.id()).reportMarkdown():projectReviews.downloadReportMarkdown(tenant,source.id());
            if(text==null||text.isBlank())throw invalid("暂无可导出的审核报告。");
            byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
            return new File("审核报告-"+source.id()+".md",()->new ByteArrayInputStream(bytes));
        }
        if(source.kind().equals("IMAGE")) {
            requireProject(tenant,source.projectId(),"AI_IMAGE_TASK:VIEW");
            var file=images.describe(source.projectId(),source.id());return stored(file.fileName(),file.objectKey());
        }
        String table;String permission;String column="storage_path";String prefix;
        switch(source.kind()) {
            case "VIDEO" -> {table="ai_video_result";permission="AI_VIDEO_RESULT:DOWNLOAD";prefix="视频";}
            case "COMPOSE" -> {table="shot_compose_result";permission="SHOT_COMPOSE:DOWNLOAD";prefix="合成视频";}
            case "EPISODE_VERSION" -> {table="episode_video_version";permission="EPISODE_VERSION:DOWNLOAD";prefix="成片";}
            case "VOICE" -> {table="ai_voice_result";permission="AI_VOICE_RESULT:DOWNLOAD";prefix="语音";}
            case "SUBTITLE" -> {table="storyboard_subtitle";permission="SUBTITLE:VIEW";column="srt_url";prefix="字幕";}
            case "VIDEO_REFERENCE" -> {table="ai_video_task_reference";permission="AI_VIDEO_TASK:VIEW";column="object_storage_path";prefix="参考素材";}
            default -> throw invalid("不支持的下载来源。");
        }
        requireProject(tenant,source.projectId(),permission);
        String condition=source.kind().equals("VIDEO_REFERENCE")?"":" and status='ACTIVE'";
        var paths=jdbc.queryForList("select "+column+" from "+table+" where tenant_id=? and project_id=? and id=?"+condition,
            String.class,tenant,source.projectId(),source.id());
        if(paths.isEmpty() || paths.get(0)==null)throw invalid("文件已删除或尚未生成。");
        String path=paths.get(0);String key;
        try{key=keys.objectKey(path.startsWith("/")?path.substring(1):path);}catch(IllegalArgumentException ex){throw invalid("原始文件不可用。");}
        if(!key.startsWith("materials/"+tenant+"/"+source.projectId()+"/")) {
            // Frozen references may come from another project; authorize that origin as well.
            var origin=java.util.regex.Pattern.compile("^materials/"+tenant+"/([1-9][0-9]*)/").matcher(key);
            if(!source.kind().equals("VIDEO_REFERENCE") || !origin.find())throw invalid("原始文件不可用。");
            requireProject(tenant,Long.valueOf(origin.group(1)),"PROJECT:VIEW");
        }
        int dot=key.lastIndexOf('.');String extension=dot>=0?key.substring(dot):".bin";
        if(!extension.matches("[.][A-Za-z0-9]{1,10}"))extension=".bin";
        return stored(prefix+"_"+source.id()+extension,key);
    }
    private File stored(String name,String key) {return new File(name,()->storage.resource(key).getInputStream());}
    private void requireProject(Long tenant,Long project,String permission) {
        if(project==null || project<1)throw invalid("缺少项目。");
        permissions.require(tenant,project,permission);
    }
    private static BusinessException invalid(String message){return new BusinessException(ErrorCode.VALIDATION_ERROR,message);}
}
