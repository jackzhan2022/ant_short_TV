package com.antshorttv.workflowagent.tool;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GlobalUnderstandingProgressWriter {
    private final JdbcTemplate jdbc;

    public GlobalUnderstandingProgressWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void update(ToolExecutionContext context, int progress, String action) {
        if (context.analysisStageId() == null || context.taskId() == null) {
            return;
        }
        jdbc.update("""
            update script_analysis_stage
               set progress_percent = ?, current_action = ?, updated_at = now()
             where id = ? and task_id = ? and stage_code = 'GLOBAL_UNDERSTANDING'
            """, progress, action, context.analysisStageId(), context.taskId());
        jdbc.update("""
            update script_analysis_task
               set current_action = ?, overall_progress = ?, updated_at = now()
             where id = ?
            """, action, Math.max(0, progress / 4), context.taskId());
    }
}
