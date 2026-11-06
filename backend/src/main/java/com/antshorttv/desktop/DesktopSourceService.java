package com.antshorttv.desktop;

import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import static com.antshorttv.desktop.DesktopSourceModels.*;

@Service
public class DesktopSourceService {
    private final JdbcTemplate jdbc;
    private final TenantContextResolver tenants;
    private final ProjectPermissionGuard guard;

    public DesktopSourceService(JdbcTemplate jdbc,TenantContextResolver tenants,ProjectPermissionGuard guard) {
        this.jdbc=jdbc; this.tenants=tenants; this.guard=guard;
    }

    public List<SourceProject> list(Long tenant) {
        tenants.requireActiveMember(tenant);
        List<SourceProject> result=new ArrayList<>();
        for (ProjectRow row:jdbc.query("select * from desktop_source_project where tenant_id=? and active=true order by id desc",this::projectRow,tenant)) {
            try { result.add(projectResponse(tenant,row)); }
            catch (BusinessException e) {
                if (e.getErrorCode()!=ErrorCode.FORBIDDEN && e.getErrorCode()!=ErrorCode.PROJECT_ACCESS_DENIED
                    && e.getErrorCode()!=ErrorCode.PROJECT_NOT_FOUND && e.getErrorCode()!=ErrorCode.NOT_FOUND) throw e;
            }
        }
        return result;
    }

    @Transactional
    public SourceProject sync(Long tenant,SyncRequest body) {
        return syncInventory(tenant,body,false);
    }

    private SourceProject syncInventory(Long tenant,SyncRequest body,boolean currentOnly) {
        tenants.requireActiveMember(tenant);
        if (body==null || body.assets()==null || body.assets().size()>5000) throw invalid("资产清单不能为空且不能超过 5000 条。");
        if(currentOnly && !Boolean.TRUE.equals(body.complete())) throw invalid("更新当前清单必须提供完整快照，历史清单未停用。");
        String connection=text(body.connectionId(),80),team=text(body.sourceTeamId(),100),project=text(body.sourceProjectId(),100),
            script=text(body.sourceScriptId(),100),name=sourceName(body.name());
        Map<String,SyncAsset> normalized=new LinkedHashMap<>();
        for (SyncAsset a:body.assets()) {
            if (a==null) throw invalid("资产条目不能为空。");
            String id=text(a.sourceAssetId(),100),parent=optional(a.sourceParentId(),100);
            SyncAsset value=new SyncAsset(id,parent,assetName(a.name()),type(a.type()));
            if (normalized.putIfAbsent(id,value)!=null) throw invalid("源资产标识重复。");
        }
        // Lock the project identity range before its individual script rows so two
        // replacements cannot each commit a different active script for one project.
        List<ProjectRow> family=currentOnly?jdbc.query("""
            select * from desktop_source_project where tenant_id=? and connection_id=? and source_team_id=?
             and source_project_id=? order by id for update
            """,this::projectRow,tenant,connection,team,project):List.of();
        for(ProjectRow sibling:family) if(sibling.active()) checkTarget(tenant,sibling);
        // Claim the stable identity before reading or validating the merged inventory. Concurrent first
        // syncs serialize here rather than both validating an absent project and later exceeding limits.
        jdbc.update("""
            insert into desktop_source_project(tenant_id,connection_id,source_team_id,source_project_id,source_script_id,name,active,synced_at)
            values(?,?,?,?,?,?,true,now()) on duplicate key update name=name
            """,tenant,connection,team,project,script,name);
        ProjectRow existing=jdbc.query("""
            select * from desktop_source_project where tenant_id=? and connection_id=? and source_team_id=?
             and source_project_id=? and source_script_id=? for update
            """,this::projectRow,tenant,connection,team,project,script).get(0);
        checkTarget(tenant,existing);
        if(currentOnly && existing.target()==null) {
            Set<Long> bindings=new HashSet<>();
            for(ProjectRow sibling:family) if(sibling.active() && sibling.target()!=null) bindings.add(sibling.target());
            if(bindings.size()==1) {
                Long binding=bindings.iterator().next();
                targetName(tenant,binding);
                jdbc.update("update desktop_source_project set target_project_id=? where id=? and tenant_id=?",binding,existing.id(),tenant);
                existing=new ProjectRow(existing.id(),existing.connection(),existing.team(),existing.sourceProject(),existing.script(),
                    existing.name(),binding,existing.active(),existing.synced());
            }
        }
        List<AssetRow> previous=rows(tenant,existing.id(),true);
        Map<String,SyncAsset> full=new HashMap<>();
        if (!Boolean.TRUE.equals(body.complete())) for (AssetRow a:previous) if(a.active()) full.put(a.sourceId(),new SyncAsset(a.sourceId(),a.parent(),a.name(),a.type()));
        full.putAll(normalized);
        for (SyncAsset a:full.values()) if(a.sourceParentId()!=null) {
            SyncAsset parent=full.get(a.sourceParentId());
            if(parent==null || parent.sourceParentId()!=null || !parent.type().equals(a.type()))
                throw invalid("变体必须引用清单中同类型的主体，且不能形成多层或循环父链。");
        }
        if (full.size()>5000) throw invalid("资产清单不能超过 5000 条。");
        Map<String,AssetRow> old=new HashMap<>(); previous.forEach(a->old.put(a.sourceId(),a));
        for (SyncAsset a:normalized.values()) {
            AssetRow before=old.get(a.sourceAssetId());
            if(before==null) jdbc.update("""
                insert into desktop_source_asset(tenant_id,source_project_row_id,source_asset_id,source_parent_id,name,asset_type,active,match_status)
                 values(?,?,?,?,?,?,true,?)
                """,tenant,existing.id(),a.sourceAssetId(),a.sourceParentId(),a.name(),a.type(),existing.target()==null?"UNBOUND":"UNMATCHED");
            else {
                boolean changed=!before.name().equals(a.name()) || !before.type().equals(a.type()) || !Objects.equals(before.parent(),a.sourceParentId());
                jdbc.update("""
                    update desktop_source_asset set source_parent_id=?,name=?,asset_type=?,active=true,match_status=? where id=? and tenant_id=?
                    """,a.sourceParentId(),a.name(),a.type(),changed && before.targetAsset()!=null?"REVIEW":before.status(),before.id(),tenant);
            }
        }
        if(Boolean.TRUE.equals(body.complete())) for(AssetRow a:previous) if(!normalized.containsKey(a.sourceId()))
            jdbc.update("update desktop_source_asset set active=false where id=? and tenant_id=?",a.id(),tenant);
        jdbc.update("update desktop_source_project set name=?,active=true,synced_at=now() where id=? and tenant_id=?",name,existing.id(),tenant);
        if(currentOnly) for(ProjectRow sibling:family) if(!sibling.id().equals(existing.id())) {
            jdbc.update("update desktop_source_asset set active=false where tenant_id=? and source_project_row_id=?",tenant,sibling.id());
            jdbc.update("update desktop_source_project set active=false where tenant_id=? and id=?",tenant,sibling.id());
        }
        return projectResponse(tenant,requireProject(tenant,existing.id(),false));
    }

    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public SourceProject syncCurrent(Long tenant,SyncRequest body) {
        try { return syncInventory(tenant,body,true); }
        catch(PessimisticLockingFailureException e) { throw invalid("当前项目正在被更新，请重新同步；历史清单未清理。"); }
    }

    public List<SourceAsset> assets(Long tenant,Long id) {
        tenants.requireActiveMember(tenant);
        ProjectRow p=requireProject(tenant,id,false);
        return new Inventory(tenant,p,new TargetCatalog(tenant)).responses();
    }

    @Transactional
    public SourceProject bind(Long tenant,Long id,Long project) {
        tenants.requireActiveMember(tenant);
        ProjectRow p=requireProject(tenant,id,true);
        if(project==null || project<=0) throw invalid("请选择目标项目。");
        targetName(tenant,project);
        if(!Objects.equals(project,p.target())) {
            jdbc.update("update desktop_source_project set target_project_id=? where id=? and tenant_id=?",project,id,tenant);
            jdbc.update("""
                update desktop_source_asset set target_asset_id=null,target_variant_id=null,target_name=null,
                 target_parent_name=null,match_status='UNMATCHED' where source_project_row_id=? and tenant_id=?
                """,id,tenant);
        }
        return projectResponse(tenant,requireProject(tenant,id,true));
    }

    @Transactional
    public List<SourceAsset> match(Long tenant,Long id) {
        tenants.requireActiveMember(tenant);
        ProjectRow p=requireProject(tenant,id,true);
        Inventory inventory=new Inventory(tenant,p,new TargetCatalog(tenant),true);
        if(p.target()==null) return inventory.responses();
        // Match subjects first so source variants only search within their resolved target subject.
        for(boolean children:List.of(false,true)) for(AssetRow a:List.copyOf(inventory.sources.values())) {
            if(!a.active() || (a.parent()!=null)!=children) continue;
            SourceAsset current=inventory.response(a);
            if("REVIEW".equals(current.matchStatus()) || a.targetAsset()!=null) continue;
            List<Target> hits=inventory.candidates(a.type(),a.parent()).stream().filter(t->t.name().trim().equals(a.name().trim())).toList();
            if(hits.size()==1) inventory.sources.put(a.sourceId(),saveMatch(tenant,a,hits.get(0)));
            else {
                String status=hits.isEmpty()?"UNMATCHED":"CONFLICT";
                jdbc.update("update desktop_source_asset set match_status=? where id=? and tenant_id=?",status,a.id(),tenant);
                inventory.sources.put(a.sourceId(),new AssetRow(a.id(),a.project(),a.sourceId(),a.parent(),a.name(),a.type(),a.active(),
                    a.targetAsset(),a.targetVariant(),a.targetName(),a.targetParentName(),status));
            }
        }
        return inventory.responses();
    }

    public List<Candidate> candidates(Long tenant,Long id,String type,String parent) {
        tenants.requireActiveMember(tenant);
        ProjectRow p=requireProject(tenant,id,false);
        String assetType=type(type);
        String sourceParent=optional(parent,100);
        return new Inventory(tenant,p,new TargetCatalog(tenant)).candidates(assetType,sourceParent).stream()
            .map(t->new Candidate(t.asset(),t.variant(),t.name(),t.type(),t.parentName(),t.prompt()!=null)).toList();
    }

    @Transactional
    public SourceAsset manualMatch(Long tenant,Long id,Long asset,Long variant) {
        tenants.requireActiveMember(tenant);
        AssetRow a=requireAsset(tenant,id);
        ProjectRow p=requireProject(tenant,a.project(),true);
        // A current read is required after waiting on the project lock under MySQL REPEATABLE READ.
        a=requireAsset(tenant,id,true);
        if(!a.active() || p.target()==null || asset==null || asset<=0) throw invalid("请先绑定项目并选择有效资产。");
        Inventory inventory=new Inventory(tenant,p,new TargetCatalog(tenant),true);
        Target selected=inventory.candidates(a.type(),a.parent()).stream()
            .filter(t->Objects.equals(t.asset(),asset) && Objects.equals(t.variant(),variant)).findFirst()
            .orElseThrow(()->invalid("所选资产类型、项目或变体主体不匹配。"));
        AssetRow matched=saveMatch(tenant,a,selected);
        inventory.sources.put(a.sourceId(),matched);
        // A subject choice changes the scope of every source variant and requires another review.
        if(a.parent()==null && !Objects.equals(a.targetAsset(),asset)) jdbc.update("""
            update desktop_source_asset set match_status='REVIEW' where tenant_id=? and source_project_row_id=?
             and source_parent_id=? and target_asset_id is not null
            """,tenant,p.id(),a.sourceId());
        return inventory.response(matched);
    }

    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public SourceAsset aiMatch(Long tenant,Long id,AiMatchRequest body) {
        try { return confirmAiMatch(tenant,id,body); }
        catch(PessimisticLockingFailureException e) {
            // Concurrent writes may invalidate a database snapshot or deadlock a candidate
            // range. Roll back and require a new human review; never retry the write.
            throw staleAiMatch();
        }
    }

    private SourceAsset confirmAiMatch(Long tenant,Long id,AiMatchRequest body) {
        tenants.requireActiveMember(tenant);
        if(body==null || body.expected()==null || body.assetId()==null || body.assetId()<=0
            || body.expected().candidates()==null || body.expected().candidates().size()>2000
            || body.expected().candidates().stream().anyMatch(Objects::isNull)
            || new HashSet<>(body.expected().candidates()).size()!=body.expected().candidates().size()) throw staleAiMatch();
        AiMatchExpected expected=body.expected();
        AssetRow a=requireAsset(tenant,id);
        ProjectRow p=requireProject(tenant,a.project(),true);
        // Use current reads after acquiring the shared desktop project lock. A repeatable-read
        // snapshot established by requireAsset may predate a competing confirmation or sync.
        a=requireAsset(tenant,id,true);
        if(!p.active() || !a.active() || p.target()==null || !Objects.equals(p.target(),expected.targetProjectId())
            || !Objects.equals(a.name(),expected.sourceName()) || !Objects.equals(a.parent(),expected.sourceParentId())
            || !Objects.equals(a.targetAsset(),expected.sourceTargetAssetId())
            || !Objects.equals(a.targetVariant(),expected.sourceTargetVariantId())) throw staleAiMatch();
        Inventory inventory=new Inventory(tenant,p,new TargetCatalog(tenant,true),true);
        String status=inventory.response(a).matchStatus();
        if(!Set.of("UNMATCHED","CONFLICT","REVIEW").contains(status)
            || !Objects.equals(status,expected.sourceMatchStatus())) throw staleAiMatch();
        if(a.parent()!=null) {
            AssetRow parent=inventory.sources.get(a.parent());
            if(parent==null || !Set.of("MATCHED","NO_PROMPT").contains(inventory.response(parent).matchStatus())) throw staleAiMatch();
        }
        List<Target> legal=inventory.candidates(a.type(),a.parent()).stream().filter(t->t.prompt()!=null).toList();
        Set<Candidate> actual=new HashSet<>(legal.stream()
            .map(t->new Candidate(t.asset(),t.variant(),t.name(),t.type(),t.parentName(),true)).toList());
        if(!actual.equals(new HashSet<>(expected.candidates()))) throw staleAiMatch();
        Target selected=legal.stream().filter(t->Objects.equals(t.asset(),body.assetId())
            && Objects.equals(t.variant(),body.variantId())).findFirst().orElseThrow(this::staleAiMatch);
        AssetRow matched=saveMatch(tenant,a,selected);
        inventory.sources.put(a.sourceId(),matched);
        if(a.parent()==null && !Objects.equals(a.targetAsset(),selected.asset())) jdbc.update("""
            update desktop_source_asset set match_status='REVIEW' where tenant_id=? and source_project_row_id=?
             and source_parent_id=? and target_asset_id is not null
            """,tenant,p.id(),a.sourceId());
        return inventory.response(matched);
    }

    private BusinessException staleAiMatch() { return invalid("AI 推荐已失效，资产、项目或候选已变化，请刷新资产并重新匹配。"); }

    public List<Prompt> prompts(Long tenant,List<Long> ids) {
        tenants.requireActiveMember(tenant);
        if(ids==null || ids.isEmpty() || ids.size()>500 || ids.stream().anyMatch(id->id==null || id<=0)
            || new HashSet<>(ids).size()!=ids.size()) throw invalid("请选择 1 至 500 个不同资产。");
        List<Prompt> result=new ArrayList<>();
        TargetCatalog targets=new TargetCatalog(tenant);
        Map<Long,Inventory> inventories=new HashMap<>();
        Map<Long,String> projectNames=new HashMap<>();
        for(Long id:ids) {
            AssetRow a=requireAsset(tenant,id);
            Inventory inventory=inventories.computeIfAbsent(a.project(),project->new Inventory(tenant,requireProject(tenant,project,false),targets));
            ProjectRow p=inventory.project;
            SourceAsset view=inventory.response(a);
            if(!"MATCHED".equals(view.matchStatus())) throw invalid("资产匹配已失效、待复核或缺少提示词，请重新匹配。");
            // Reuse the exact target snapshot validated above; do not reread a renamed target's prompt.
            Target t=inventory.resolve(a);
            if(t==null || t.prompt()==null) throw invalid("目标资产提示词为空。");
            AssetRow parent=a.parent()==null?null:inventory.sources.get(a.parent());
            result.add(new Prompt(a.id(),a.name(),a.type(),projectNames.computeIfAbsent(p.target(),project->targetName(tenant,project)),
                p.target(),t.asset(),t.variant(),t.prompt(),p.id(),p.name(),parent==null?null:parent.name()));
        }
        return result;
    }

    /** Source mappings and target prompts are cached only for this operation. */
    private final class Inventory {
        private final ProjectRow project;
        private final TargetCatalog targets;
        private final Map<String,AssetRow> sources=new LinkedHashMap<>();
        private Inventory(Long tenant,ProjectRow project,TargetCatalog targets) {
            this(tenant,project,targets,false);
        }
        private Inventory(Long tenant,ProjectRow project,TargetCatalog targets,boolean currentRead) {
            this.project=project; this.targets=targets;
            rows(tenant,project.id(),currentRead).forEach(a->sources.put(a.sourceId(),a));
        }
        private List<SourceAsset> responses() { return sources.values().stream().map(this::response).toList(); }
        private SourceAsset response(AssetRow a) {
        String status;
        Target target=null;
        if(!a.active()) status="INACTIVE";
        else if(project.target()==null) status="UNBOUND";
        else if("REVIEW".equals(a.status())) status="REVIEW";
        else if(a.targetAsset()!=null) {
            target=resolve(a);
            status=target==null || !Objects.equals(target.name(),a.targetName())
                || !Objects.equals(target.parentName(),a.targetParentName())?"REVIEW":target.prompt()==null?"NO_PROMPT":"MATCHED";
        } else status=Set.of("CONFLICT","UNMATCHED").contains(a.status())?a.status():"UNMATCHED";
        return new SourceAsset(a.id(),a.sourceId(),a.parent(),a.name(),a.type(),a.active(),a.targetAsset(),a.targetVariant(),
            a.targetName(),status,target!=null && target.prompt()!=null && !"REVIEW".equals(status));
        }

        private Target resolve(AssetRow a) {
        return candidates(a.type(),a.parent()).stream()
            .filter(t->Objects.equals(t.asset(),a.targetAsset()) && Objects.equals(t.variant(),a.targetVariant())).findFirst().orElse(null);
        }

        private List<Target> candidates(String type,String parent) {
            if(project.target()==null) return List.of();
            if(parent==null) return targets.get(project.target(),type,null);
            AssetRow sourceParent=sources.get(parent);
            if(sourceParent==null || !sourceParent.active() || sourceParent.parent()!=null || !sourceParent.type().equals(type))
                throw invalid("源变体的主体不存在。");
            SourceAsset parentView=response(sourceParent);
            if(sourceParent.targetAsset()==null || "REVIEW".equals(parentView.matchStatus())) return List.of();
            return targets.get(project.target(),type,sourceParent.targetAsset());
        }
    }

    private final class TargetCatalog {
        private final Long tenant;
        private final boolean currentRead;
        private final Map<TargetScope,List<Target>> scopes=new HashMap<>();
        private TargetCatalog(Long tenant) { this(tenant,false); }
        private TargetCatalog(Long tenant,boolean currentRead) { this.tenant=tenant; this.currentRead=currentRead; }
        private List<Target> get(Long project,String type,Long parent) {
            return scopes.computeIfAbsent(new TargetScope(project,type,parent),scope->{
                String table=table(type);
                // Lock rows and candidate ranges for AI confirmation, including currently empty
                // prompts. REPEATABLE_READ next-key locks prevent candidate inserts during save.
                if(parent==null) return jdbc.query("select id,name,prompt from "+table+" where tenant_id=? and project_id=? and deleted_at is null order by id"+(currentRead?" for update":""),
                    (rs,n)->new Target(rs.getLong("id"),null,rs.getString("name"),type,null,nonblank(rs.getString("prompt"))),tenant,project);
                return jdbc.query("""
            select v.id,v.asset_id,v.name,v.prompt,v.is_primary,a.name parent_name,a.prompt base_prompt
             from asset_visual_variant v join %s a on a.id=v.asset_id and a.tenant_id=v.tenant_id and a.project_id=v.project_id
             where v.tenant_id=? and v.project_id=? and v.asset_type=? and v.asset_id=? and v.deleted_at is null
              and a.deleted_at is null order by v.id
            """.formatted(table)+(currentRead?" for update":""),(rs,n)->{
                String base=nonblank(rs.getString("base_prompt")),delta=nonblank(rs.getString("prompt"));
                String prompt=rs.getBoolean("is_primary")?base:delta;
                return new Target(rs.getLong("asset_id"),rs.getLong("id"),rs.getString("name"),type,rs.getString("parent_name"),prompt);
            },tenant,project,type,parent);
            });
        }
    }

    private AssetRow saveMatch(Long tenant,AssetRow a,Target t) {
        jdbc.update("""
            update desktop_source_asset set target_asset_id=?,target_variant_id=?,target_name=?,target_parent_name=?,match_status=?
             where id=? and tenant_id=?
            """,t.asset(),t.variant(),t.name(),t.parentName(),t.prompt()==null?"NO_PROMPT":"MATCHED",a.id(),tenant);
        return new AssetRow(a.id(),a.project(),a.sourceId(),a.parent(),a.name(),a.type(),a.active(),t.asset(),t.variant(),t.name(),t.parentName(),t.prompt()==null?"NO_PROMPT":"MATCHED");
    }

    private ProjectRow requireProject(Long tenant,Long id,boolean lock) {
        List<ProjectRow> values=jdbc.query("select * from desktop_source_project where id=? and tenant_id=?"+(lock?" for update":""),this::projectRow,id,tenant);
        if(values.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND,"源项目不存在。");
        if(!values.get(0).active()) throw invalid("历史资产清单已停用，请刷新并选择项目当前清单。");
        checkTarget(tenant,values.get(0));
        return values.get(0);
    }
    private void checkTarget(Long tenant,ProjectRow p) { if(p.target()!=null) targetName(tenant,p.target()); }
    private String targetName(Long tenant,Long project) {
        guard.require(tenant,project,"ELEMENT:VIEW");
        List<String> names=jdbc.queryForList("select name from project where id=? and tenant_id=? and deleted_at is null",String.class,project,tenant);
        if(names.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND,"目标项目不存在。");
        return names.get(0);
    }
    private SourceProject projectResponse(Long tenant,ProjectRow p) {
        return new SourceProject(p.id(),p.connection(),p.name(),p.team(),p.sourceProject(),p.script(),p.target(),
            p.target()==null?null:targetName(tenant,p.target()),p.active(),p.synced());
    }
    private AssetRow requireAsset(Long tenant,Long id) {
        return requireAsset(tenant,id,false);
    }
    private AssetRow requireAsset(Long tenant,Long id,boolean currentRead) {
        List<AssetRow> rows=jdbc.query("select * from desktop_source_asset where id=? and tenant_id=?"+(currentRead?" for update":""),this::assetRow,id,tenant);
        if(rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND,"源资产不存在。");
        return rows.get(0);
    }
    private List<AssetRow> rows(Long tenant,Long id,boolean currentRead) {
        return jdbc.query("select * from desktop_source_asset where tenant_id=? and source_project_row_id=? order by id"+(currentRead?" for update":""),this::assetRow,tenant,id);
    }
    private ProjectRow projectRow(ResultSet r,int n) throws SQLException {
        return new ProjectRow(r.getLong("id"),r.getString("connection_id"),r.getString("source_team_id"),r.getString("source_project_id"),
            r.getString("source_script_id"),r.getString("name"),r.getObject("target_project_id",Long.class),r.getBoolean("active"),r.getTimestamp("synced_at").toLocalDateTime());
    }
    private AssetRow assetRow(ResultSet r,int n) throws SQLException {
        return new AssetRow(r.getLong("id"),r.getLong("source_project_row_id"),r.getString("source_asset_id"),r.getString("source_parent_id"),
            r.getString("name"),r.getString("asset_type"),r.getBoolean("active"),r.getObject("target_asset_id",Long.class),r.getObject("target_variant_id",Long.class),
            r.getString("target_name"),r.getString("target_parent_name"),r.getString("match_status"));
    }
    private String type(String value) { if(!Set.of("CHARACTER","SCENE","PROP").contains(value==null?"":value)) throw invalid("资产类型无效。"); return value; }
    private String table(String type) { return switch(type(type)) { case "CHARACTER"->"character_asset";case "SCENE"->"scene_asset";default->"prop_asset"; }; }
    private String text(String value,int max) { if(value==null || value.isBlank() || value.length()>max || value.chars().anyMatch(Character::isISOControl)) throw invalid("字段为空、过长或包含控制字符。"); return value.trim(); }
    private String sourceName(String value) { text(value,200); return value; }
    private String assetName(String value) {
        if(value==null || value.isBlank() || value.length()>200
            || value.chars().anyMatch(c->Character.isISOControl(c) && c!='\r' && c!='\n' && c!='\t'))
            throw invalid("资产名称必须为 1～200 个字符且不能全为空白；控制字符仅允许换行、回车和制表符。");
        return value;
    }
    private String optional(String value,int max) { return value==null || value.isBlank()?null:text(value,max); }
    private String nonblank(String value) { return value==null || value.isBlank()?null:value.trim(); }
    private BusinessException invalid(String message) { return new BusinessException(ErrorCode.VALIDATION_ERROR,message); }
    private record ProjectRow(Long id,String connection,String team,String sourceProject,String script,String name,Long target,boolean active,LocalDateTime synced) {}
    private record AssetRow(Long id,Long project,String sourceId,String parent,String name,String type,boolean active,Long targetAsset,Long targetVariant,String targetName,String targetParentName,String status) {}
    private record Target(Long asset,Long variant,String name,String type,String parentName,String prompt) {}
    private record TargetScope(Long project,String type,Long parent) {}
}
