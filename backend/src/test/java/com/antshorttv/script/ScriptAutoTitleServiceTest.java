package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.antshorttv.ai.*;
import com.antshorttv.querycache.QueryResultCache;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ScriptAutoTitleServiceTest {
    JdbcTemplate db;
    AiInvocationService ai;
    ScriptAutoTitleService service;

    @BeforeEach void setup() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:title" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        db = new JdbcTemplate(ds);
        db.execute("create table project(id bigint, tenant_id bigint, name varchar(200), deleted_at timestamp, updated_at timestamp)");
        db.execute("create table script(id bigint, tenant_id bigint, project_id bigint, title varchar(200), content clob, created_by bigint, deleted_at timestamp, updated_at timestamp)");
        ai = mock(AiInvocationService.class);
        service = new ScriptAutoTitleService(db, ai, new TransactionTemplate(new DataSourceTransactionManager(ds)), QueryResultCache.disabled());
    }
    void seed(String project, String title, String content) {
        db.update("insert into project(id,tenant_id,name) values(1,2,?)", project);
        db.update("insert into script(id,tenant_id,project_id,title,content,created_by) values(3,2,1,?,?,4)",title,content);
    }
    String name() { return db.queryForObject("select name from project",String.class); }
    String title() { return db.queryForObject("select title from script",String.class); }

    @Test void extractsOriginalTitleWithoutAi() {
        seed("未命名短剧","未命名短剧","剧名：《归途》\n第一集");
        service.nameProject(2L,1L);
        assertThat(name()).isEqualTo("归途");
        assertThat(title()).isEqualTo("归途");
        verifyNoInteractions(ai);
    }
    @Test void keepsBothManualNames() {
        seed("我的项目","我的剧本","剧名：归途");
        service.nameProject(2L,1L);
        assertThat(name()).isEqualTo("我的项目");
        assertThat(title()).isEqualTo("我的剧本");
        verifyNoInteractions(ai);
    }
    @Test void keepsManualScriptTitleWhileNamingProjectFromOriginal() {
        seed("未命名短剧","人工剧名","剧名：归途");
        service.nameProject(2L,1L);
        assertThat(name()).isEqualTo("归途");
        assertThat(title()).isEqualTo("人工剧名");
        verifyNoInteractions(ai);
    }
    @Test void ignoresContentWithoutStory() {
        seed("未命名短剧","未命名剧本","1111");
        service.nameProject(2L,1L);
        assertThat(name()).isEqualTo("未命名短剧");
        verifyNoInteractions(ai);
    }
    @Test void freeInvocationIsBoundedAndConcurrentRenameIsPreserved() {
        seed("未命名短剧","未命名剧本","甲".repeat(5000)+"不应发送");
        when(ai.invokeText(any())).thenAnswer(call -> {
            AiInvocationRequest request = call.getArgument(0);
            assertThat(request.executionId()).isNull(); // no point reservation or settlement
            assertThat(request.businessSceneCode()).isEqualTo("script_auto_title_free");
            assertThat(request.textRequest().userPrompt()).doesNotContain("不应发送");
            db.update("update project set name='人工改名' where id=1");
            return AiInvocationResult.text("script_auto_title_free",new AiTextResponse("归途",null,1,1,2,1L,java.util.Map.of()),1L,null);
        });
        service.nameProject(2L,1L);
        assertThat(name()).isEqualTo("人工改名");
        assertThat(title()).isEqualTo("归途");
    }
    @Test void failureAndEmptyContentDoNotChangeNames() {
        seed("未命名短剧","未命名剧本","");
        service.nameProject(2L,1L);
        verifyNoInteractions(ai);
        db.update("update script set content='剧情'");
        when(ai.invokeText(any())).thenThrow(new IllegalStateException("unavailable"));
        service.nameProject(2L,1L);
        assertThat(name()).isEqualTo("未命名短剧");
        assertThat(title()).isEqualTo("未命名剧本");
    }
}
