package com.antshorttv.productiontask;

import java.nio.file.Files;
import java.nio.file.Path;

/** Exports the exact application read model, avoiding a divergent benchmark copy. */
public class ExportQueries {
    public static void main(String[] args) throws Exception {
        Path output=Path.of(args[0]);
        Files.createDirectories(output);
        String from=" from ("+ProductionTaskSources.ALL.replace(":tenant", "1")+") t where t.tenant_id=1";
        String roots=" and t.parent_id is null and t.root_visible=true";
        String order=" order by case when t.status_group in ('RUNNING','QUEUED') then 0 else 1 end,t.created_at desc,t.type,t.id desc";
        Files.writeString(output.resolve("list_team.sql"),"select t.*"+from+roots+order+" limit 20 offset 0");
        Files.writeString(output.resolve("list_mine.sql"),"select t.*"+from+roots+" and t.created_by=1"+order+" limit 20 offset 0");
        Files.writeString(output.resolve("list_last.sql"),"select t.*"+from+roots+order+" limit 20 offset 100");
        Files.writeString(output.resolve("filtered.sql"),"select t.*"+from+roots+" and t.status_group='FAILED' and t.created_at>='2026-01-01'"+order+" limit 20 offset 0");
        Files.writeString(output.resolve("count.sql"),"select count(*)"+from+roots);
        Files.writeString(output.resolve("summary.sql"),"select t.status_group,count(*)"+from+roots+" group by t.status_group");
        Files.writeString(output.resolve("detail.sql"),"select t.*"+from+" and t.type='SCRIPT_OPERATION' and t.id=(select min(id) from script_ai_operation where tenant_id=1)");
        Files.writeString(output.resolve("children.sql"),"select t.*"+from+" and t.parent_type='VIDEO_DECOMPOSITION' and t.parent_id=(select min(id) from video_decomposition_batch where tenant_id=1)"+order+" limit 20 offset 0");
    }
}
