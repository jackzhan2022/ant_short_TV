package com.antshorttv.downloadcenter;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Resolve display names only after the source access checks have succeeded. */
final class DownloadFileNames {
    private final JdbcTemplate jdbc;
    DownloadFileNames(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    String name(Long tenant, DownloadTask.Source source, String original) {
        String extension=extension(original), label="文件";
        Long project=source.projectId();
        switch(source.kind()) {
            case "FREE_CREATION", "FREE_REFERENCE" -> {
                var rows=jdbc.queryForList("select media_type,prompt from free_creation where tenant_id=? and id=?",tenant,source.parentId());
                String media=rows.isEmpty()?"文件":switch(text(rows.get(0),"media_type")) {case "IMAGE"->"图片";case "VIDEO"->"视频";default->"文件";};
                String prompt=rows.isEmpty()?"":shortText(text(rows.get(0),"prompt"),24);
                label=source.kind().equals("FREE_REFERENCE")?"自由创作_参考素材_"+stem(original):"自由创作_"+media+(prompt.isBlank()?"":"_"+prompt);
                label+="_作品"+source.parentId();
            }
            case "IMAGE" -> {
                label="图片";
                var rows=jdbc.queryForList("select target_type,target_id from ai_image_result where tenant_id=? and project_id=? and id=?",tenant,project,source.id());
                if(!rows.isEmpty()) {
                    var row=rows.get(0);String type=text(row,"target_type");Object target=row.get("target_id");
                    if(type.equals("VISUAL_VARIANT")) {
                        var variants=jdbc.queryForList("select asset_type,asset_id,name from asset_visual_variant where tenant_id=? and project_id=? and id=?",tenant,project,target);
                        if(!variants.isEmpty()) {
                            var variant=variants.get(0);String asset=asset(tenant,project,text(variant,"asset_type"),variant.get("asset_id"));
                            label=join(asset,text(variant,"name"),"图片");
                        }
                    } else if(type.equals("STORYBOARD")) label=join(shot(tenant,project,target),"图片");
                    else label=join(asset(tenant,project,type,target),"图片");
                }
            }
            case "VIDEO", "COMPOSE", "VOICE", "SUBTITLE", "VIDEO_REFERENCE" -> {
                String table=switch(source.kind()) {case "VIDEO"->"ai_video_result";case "COMPOSE"->"shot_compose_result";case "VOICE"->"ai_voice_result";case "SUBTITLE"->"storyboard_subtitle";default->"ai_video_task_reference";};
                String kind=switch(source.kind()) {case "VIDEO"->"视频";case "COMPOSE"->"合成视频";case "VOICE"->"配音";case "SUBTITLE"->"字幕";default->"参考素材";};
                var rows=jdbc.queryForList("select storyboard_id"+(source.kind().equals("VIDEO_REFERENCE")?",display_name":"")+" from "+table+" where tenant_id=? and project_id=? and id=?",tenant,project,source.id());
                label=rows.isEmpty()?kind:join(shot(tenant,project,rows.get(0).get("storyboard_id")),kind,source.kind().equals("VIDEO_REFERENCE")?text(rows.get(0),"display_name"):"");
            }
            case "EPISODE_VERSION" -> {
                var rows=jdbc.queryForList("select episode_no,version_no from episode_video_version where tenant_id=? and project_id=? and id=?",tenant,project,source.id());
                label=rows.isEmpty()?"成片":String.format(Locale.ROOT,"第%02d集_成片_v%s",((Number)rows.get(0).get("episode_no")).intValue(),rows.get(0).get("version_no"));
            }
            case "REVIEW", "PROJECT_REVIEW" -> {
                var rows=jdbc.queryForList("select p.name,t.round_no from review_task t join review_project p on p.id=t.project_id and p.tenant_id=t.tenant_id where t.tenant_id=? and t.id=?",tenant,source.id());
                label=rows.isEmpty()?"审核报告":join(text(rows.get(0),"name"),"第"+rows.get(0).get("round_no")+"轮审核报告");project=null;
            }
            default -> label=stem(original);
        }
        return DownloadArchiveService.uniqueName(join(project(tenant,project),label)+"_"+source.id()+extension,new HashSet<>());
    }
    String bundle(Long tenant,List<DownloadTask.Source> sources,String requested) {
        Long project=sources.get(0).projectId();
        if(project!=null && sources.stream().allMatch(s->project.equals(s.projectId()))) {
            String name=project(tenant,project);return requested.contains(name)?requested:join(name,requested);
        }
        return requested;
    }
    private String project(Long tenant,Long project) {
        if(project==null)return "";
        var rows=jdbc.queryForList("select name from project where tenant_id=? and id=?",String.class,tenant,project);
        return rows.isEmpty()?"项目"+project:rows.get(0);
    }
    private String shot(Long tenant,Long project,Object id) {
        var rows=jdbc.queryForList("select episode_no,shot_no from storyboard where tenant_id=? and project_id=? and id=?",tenant,project,id);
        return rows.isEmpty()?"":String.format(Locale.ROOT,"第%02d集_分镜%03d",((Number)rows.get(0).get("episode_no")).intValue(),((Number)rows.get(0).get("shot_no")).intValue());
    }
    private String asset(Long tenant,Long project,String type,Object id) {
        String table=switch(type) {case "CHARACTER"->"character_asset";case "SCENE"->"scene_asset";case "PROP"->"prop_asset";default->null;};
        if(table==null)return "";
        var rows=jdbc.queryForList("select name from "+table+" where tenant_id=? and project_id=? and id=?",String.class,tenant,project,id);
        return rows.isEmpty()?"":rows.get(0);
    }
    static String extension(String name) {
        if(name==null)return ".bin";int dot=name.lastIndexOf('.');
        String ext=dot>=0?name.substring(dot):"";return ext.matches("[.][A-Za-z0-9]{1,10}")?ext.toLowerCase(Locale.ROOT):".bin";
    }
    static String stem(String name) {if(name==null)return "文件";String ext=extension(name);return name.toLowerCase(Locale.ROOT).endsWith(ext)?name.substring(0,name.length()-ext.length()):name;}
    private static String text(Map<String,Object> row,String key){return Objects.toString(row.get(key),"");}
    private static String shortText(String value,int length){String s=value.replaceAll("\\s+"," ").strip();return s.codePoints().limit(length).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString();}
    private static String join(String...parts){return String.join("_",Arrays.stream(parts).filter(s->s!=null&&!s.isBlank()).toList());}
}
