package db.migration;

import java.sql.Connection;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

public class V135__unify_storyboard_skills extends BaseJavaMigration {
    private static final String CODE = "short-drama-storyboard-production";
    private static final List<String> LEGACY = List.of(
        "short-drama-storyboard-planning", "short-drama-seedance-video-prompt");

    @Override public boolean canExecuteInTransaction() { return false; }

    @Override public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        jdbc.execute("""
            create table if not exists tenant_workflow_skill_merge_archive (
                tenant_id bigint not null, skill_code varchar(128) not null,
                name varchar(200) not null, description varchar(2000) not null,
                content longtext not null, revision varchar(64) not null,
                created_by bigint not null, updated_by bigint not null,
                created_at datetime not null, updated_at datetime not null,
                archived_at datetime not null,
                primary key (tenant_id, skill_code, revision)
            )
            """);
        boolean ownsTransaction = connection.getAutoCommit();
        if (ownsTransaction) connection.setAutoCommit(false);
        Savepoint savepoint = ownsTransaction ? null : connection.setSavepoint();
        try {
            mergeTenants(jdbc);
            mergeAgent(jdbc);
            if (ownsTransaction) connection.commit();
        } catch (Exception failure) {
            if (ownsTransaction) connection.rollback();
            else connection.rollback(savepoint);
            throw failure;
        } finally {
            if (ownsTransaction) connection.setAutoCommit(true);
            else connection.releaseSavepoint(savepoint);
        }
    }

    private void mergeTenants(JdbcTemplate jdbc) {
        Map<Long, List<Row>> tenants = new LinkedHashMap<>();
        for (Row row : rows(jdbc, "skill_code in (?,?) order by tenant_id,skill_code for update", LEGACY.toArray())) {
            tenants.computeIfAbsent(row.tenantId(), ignored -> new ArrayList<>()).add(row);
        }
        for (var entry : tenants.entrySet()) {
            List<Row> old = entry.getValue();
            old.sort(Comparator.comparingInt(row -> LEGACY.indexOf(row.code())));
            for (Row row : old) archive(jdbc, row);
            Row existing = rows(jdbc, "tenant_id=? and skill_code=? for update", entry.getKey(), CODE)
                .stream().findFirst().orElse(null);
            List<Row> sources = new ArrayList<>(old);
            if (existing != null) {
                archive(jdbc, existing);
                sources.add(existing);
            }
            Row first = sources.stream().min(Comparator.comparing(Row::createdAt)).orElseThrow();
            Row last = sources.stream().max(Comparator.comparing(Row::updatedAt)).orElseThrow();
            StringBuilder body = new StringBuilder();
            for (Row row : old) {
                String marker = "<!-- unified-storyboard-source:" + row.code() + ":" + row.revision() + " -->";
                if (existing != null && existing.content().contains(marker)) continue;
                body.append("\n\n").append(marker).append("\n## ")
                    .append(row.code().equals(LEGACY.get(0)) ? "原整集分镜规划补充" : "原视频提示词表达补充")
                    .append("\n\n").append(markdown(row.content()));
            }
            if (existing != null) body.append("\n\n## 已有统一分镜补充\n\n").append(markdown(existing.content()));
            String defaultHeader = "---\nname: 短剧分镜制作\ndescription: 本团队的统一分镜创作补充规则\n---\n";
            String existingRaw = existing == null ? "" : existing.content().replace("\r\n", "\n").replace('\r', '\n');
            int headerEnd = existingRaw.startsWith("---\n") ? existingRaw.indexOf("\n---\n", 4) : -1;
            String content = (headerEnd < 0 ? defaultHeader : existingRaw.substring(0, headerEnd + 5)) + body + "\n";
            String revision = UUID.randomUUID().toString();
            if (existing == null) {
                jdbc.update("""
                    insert into tenant_workflow_skill
                      (tenant_id,skill_code,name,description,content,revision,created_by,updated_by,created_at,updated_at)
                    values (?,?,'短剧分镜制作','本团队的统一分镜创作补充规则',?,?,?,?,?,?)
                    """, entry.getKey(), CODE, content, revision, first.createdBy(), last.updatedBy(), first.createdAt(), last.updatedAt());
            } else {
                int changed = jdbc.update("""
                    update tenant_workflow_skill set content=?,revision=?,created_by=?,updated_by=?,created_at=?,updated_at=?
                     where tenant_id=? and skill_code=? and revision=?
                    """, content, revision, first.createdBy(), last.updatedBy(), first.createdAt(), last.updatedAt(),
                    entry.getKey(), CODE, existing.revision());
                if (changed != 1) throw new IllegalStateException("Unified tenant Skill changed during migration");
            }
            for (Row row : old) {
                int removed = jdbc.update("delete from tenant_workflow_skill where tenant_id=? and skill_code=? and revision=?",
                    row.tenantId(), row.code(), row.revision());
                if (removed != 1) throw new IllegalStateException("Legacy tenant Skill changed during migration");
            }
        }
    }

    private void mergeAgent(JdbcTemplate jdbc) {
        for (Long agentId : jdbc.queryForList("select id from ai_workflow_agent where code='short-drama-storyboard' for update", Long.class)) {
            List<Map<String, Object>> bindings = jdbc.queryForList(
                "select skill_code,load_order from ai_workflow_agent_skill where agent_id=? order by load_order", agentId);
            List<String> known = List.of("short-drama-analysis-foundation", LEGACY.get(0),
                "short-drama-storyboard-material-reference", LEGACY.get(1));
            List<Map<String, Object>> old = bindings.stream().filter(row -> known.contains(row.get("skill_code"))).toList();
            boolean unified = bindings.stream().anyMatch(row -> CODE.equals(row.get("skill_code")));
            if (old.isEmpty() && unified) continue;
            int order = old.stream().mapToInt(row -> ((Number) row.get("load_order")).intValue()).min()
                .orElseGet(() -> bindings.stream().mapToInt(row -> ((Number) row.get("load_order")).intValue()).max().orElse(-1) + 1);
            for (var row : old) jdbc.update("delete from ai_workflow_agent_skill where agent_id=? and skill_code=?", agentId, row.get("skill_code"));
            if (unified) {
                jdbc.update("update ai_workflow_agent_skill set load_order=? where agent_id=? and skill_code=?", order, agentId, CODE);
            } else {
                jdbc.update("insert into ai_workflow_agent_skill(agent_id,skill_code,load_order,created_at) values (?,?,?,now())", agentId, CODE, order);
            }
            jdbc.update("update ai_workflow_agent set revision=revision+1,updated_at=now() where id=?", agentId);
        }
    }

    private void archive(JdbcTemplate jdbc, Row row) {
        Integer count = jdbc.queryForObject("select count(*) from tenant_workflow_skill_merge_archive where tenant_id=? and skill_code=? and revision=?",
            Integer.class, row.tenantId(), row.code(), row.revision());
        if (count != null && count > 0) return;
        jdbc.update("""
            insert into tenant_workflow_skill_merge_archive
              (tenant_id,skill_code,name,description,content,revision,created_by,updated_by,created_at,updated_at,archived_at)
            values (?,?,?,?,?,?,?,?,?,?,now())
            """, row.tenantId(), row.code(), row.name(), row.description(), row.content(), row.revision(),
            row.createdBy(), row.updatedBy(), row.createdAt(), row.updatedAt());
    }

    private List<Row> rows(JdbcTemplate jdbc, String where, Object... args) {
        return jdbc.query("select * from tenant_workflow_skill where " + where, (rs, number) ->
            new Row(rs.getLong("tenant_id"), rs.getString("skill_code"), rs.getString("name"),
                rs.getString("description"), rs.getString("content"), rs.getString("revision"),
                rs.getLong("created_by"), rs.getLong("updated_by"), rs.getTimestamp("created_at"), rs.getTimestamp("updated_at")), args);
    }

    private String markdown(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        int end = normalized.startsWith("---\n") ? normalized.indexOf("\n---\n", 4) : -1;
        return (end < 0 ? normalized : normalized.substring(end + 5)).strip();
    }

    private record Row(long tenantId, String code, String name, String description, String content,
        String revision, long createdBy, long updatedBy, Timestamp createdAt, Timestamp updatedAt) { }
}
