package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;
import com.antshorttv.common.PageBounds;
import com.antshorttv.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;

@SpringBootTest
class VideoDecompositionPagingTest {
    @Autowired private VideoDecompositionBatchMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @org.springframework.boot.test.mock.mockito.MockBean private ObjectStorageService storage;

    @Test
    void filtersProjectPermissionsBeforeCountAndPage() {
        for (int i = 0; i < 25; i++) {
            jdbc.update("insert into video_decomposition_batch (tenant_id,name,status,total_episodes,completed_episodes,failed_episodes,created_by,created_at,updated_at) values (991,'Paging','PENDING',0,0,0,1,now(),now())");
        }
        assertThat(mapper.countVisible(991L, 1L, false, null)).isEqualTo(25);
        assertThat(mapper.selectVisiblePage(991L, 1L, false, null, PageBounds.of(2, 20))).hasSize(5);
        assertThat(mapper.selectVisiblePage(991L, 1L, false, null, PageBounds.of(Integer.MAX_VALUE, 100))).isEmpty();
        jdbc.update("insert into project (tenant_id,name,code,owner_id,status,created_by,created_at,updated_at) values (991,'Hidden','PAGING_HIDDEN',1,'ACTIVE',1,now(),now())");
        Long project = jdbc.queryForObject("select max(id) from project", Long.class);
        jdbc.update("insert into video_decomposition_batch (tenant_id,project_id,name,status,total_episodes,completed_episodes,failed_episodes,created_by,created_at,updated_at) values (991,?,'Hidden','PENDING',3,1,1,1,now(),now())", project);
        Long batch = jdbc.queryForObject("select max(id) from video_decomposition_batch", Long.class);
        assertThat(mapper.countVisible(991L, 1L, false, null)).isEqualTo(25);
        assertThat(mapper.countVisible(991L, 1L, true, null)).isEqualTo(26);
        assertThat(mapper.countVisible(991L, 1L, false, project)).isZero();
        assertThat(mapper.countVisible(992L, 1L, true, null)).isZero();
        jdbc.update("insert into project_role (tenant_id,project_id,name,code,is_system,status,created_by,created_at,updated_at) values (991,?,'Viewer','VIEWER',false,'ACTIVE',1,now(),now())", project);
        Long role = jdbc.queryForObject("select max(id) from project_role", Long.class);
        jdbc.update("insert ignore into permission (code,name,type,resource,action,created_at,updated_at) values ('PROJECT:VIEW','View project','PAGE','PROJECT','VIEW',now(),now())");
        Long permission = jdbc.queryForObject("select id from permission where code = 'PROJECT:VIEW'", Long.class);
        jdbc.update("insert into project_role_permission (tenant_id,project_id,role_id,permission_id,created_at) values (991,?,?,?,now())", project, role, permission);
        jdbc.update("insert into project_member (tenant_id,project_id,user_id,role_id,joined_at,status,created_by,created_at,updated_at) values (991,?,1,?,now(),'ACTIVE',1,now(),now())", project, role);
        assertThat(mapper.countVisible(991L, 1L, false, null)).isEqualTo(26);
        jdbc.update("update project_member set status = 'REMOVED' where tenant_id = 991 and project_id = ?", project);
        assertThat(mapper.countVisible(991L, 1L, false, null)).isEqualTo(25);
        int episode = 1;
        for (String status : List.of("SUCCEEDED", "FAILED", "ANALYZING")) {
            jdbc.update("insert into video_decomposition_episode (batch_id,tenant_id,project_id,episode_no,source_file_name,storage_path,file_size,status,analysis_version,draft_version,created_by,created_at,updated_at) values (?,991,?,?,'video.mp4','video.mp4',100,?,0,0,1,now(),now())", batch, project, episode++, status);
        }
        var statistics = mapper.selectStatistics(991L, List.of(batch)).get(0);
        assertThat(statistics.succeeded).isEqualTo(1);
        assertThat(statistics.failed).isEqualTo(1);
        assertThat(statistics.processing).isEqualTo(1);
        assertThat(statistics.progressSum).isEqualTo(150);
    }
}
