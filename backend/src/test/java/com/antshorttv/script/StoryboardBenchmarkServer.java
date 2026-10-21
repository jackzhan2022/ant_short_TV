package com.antshorttv.script;

import com.antshorttv.AntShortTvApplication;
import com.antshorttv.user.UserMapper;
import com.antshorttv.authsession.AuthSessionService;
import com.antshorttv.rbac.RbacService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Manual, synthetic, loopback-only benchmark. Run with the test classpath; never a deployed component. */
public class StoryboardBenchmarkServer {
    public static void main(String[] args) throws Exception {
        var context = SpringApplication.run(AntShortTvApplication.class,
            "--server.address=127.0.0.1", "--server.port=18189",
            "--spring.datasource.url=jdbc:h2:mem:storyboard_benchmark;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "--spring.datasource.username=sa", "--spring.datasource.password=",
            "--spring.datasource.driver-class-name=org.h2.Driver",
            "--spring.config.import=", "--monitoring.enabled=false", "--query-cache.enabled=false",
            "--server.compression.enabled=true", "--server.compression.min-response-size=1024",
            "--server.compression.mime-types=application/json");
        var jdbc = context.getBean(JdbcTemplate.class);
        try (var connection = jdbc.getDataSource().getConnection()) {
            if (!connection.getMetaData().getURL().startsWith("jdbc:h2:mem:storyboard_benchmark")) {
                context.close(); throw new IllegalStateException("Benchmark requires its dedicated in-memory database");
            }
        }
        var json = context.getBean(ObjectMapper.class);
        String password = context.getBean(PasswordEncoder.class).encode("BenchmarkOnly123");
        jdbc.update("""
            insert into app_user(id,mobile,password_hash,nickname,status,created_at,updated_at)
            values (9001,'13800000199',?,'Local Benchmark','ACTIVE',now(),now())
            """, password);
        jdbc.update("insert into tenant(id,code,name,type,status,owner_member_id,created_at,updated_at) values (9001,'BENCHMARK','Local Benchmark','STUDIO','ACTIVE',9001,now(),now())");
        jdbc.update("insert into tenant_member(id,tenant_id,user_id,member_type,status,joined_at,created_at,updated_at) values (9001,9001,9001,'OWNER','ACTIVE',now(),now(),now())");
        jdbc.update("""
            insert into project(id,tenant_id,name,code,owner_id,status,aspect_ratio,video_resolution,
                visual_style,initial_script_content,created_by,created_at,updated_at)
            values (52,9001,'85 Episodes / 851 Storyboards','BENCHMARK',9001,'ACTIVE','9:16','720p',
                '真人写实',?,9001,now(),now())
            """, "Synthetic script body. ".repeat(13000));
        jdbc.update("insert into script(id,tenant_id,project_id,title,source_type,content,status,created_by,created_at,updated_at) values (52,9001,52,'Benchmark','UPLOAD',?,'DRAFT',9001,now(),now())", "Synthetic script body. ".repeat(13000));
        jdbc.update("insert into team_point_account(tenant_id,balance,total_granted,total_consumed,created_at,updated_at) values (9001,10000,10000,0,now(),now())");
        for (int actor=1;actor<=2;actor++) {
            jdbc.update("""
                insert into character_asset(id,tenant_id,project_id,script_id,name,role_type,status,main_image_url,
                    created_by,created_at,updated_at)
                values (?,9001,52,52,?,'MAIN','CONFIRMED','http://127.0.0.1:18190/__benchmark_asset.svg',9001,now(),now())
                """, actor,"角色"+actor);
            jdbc.update("""
                insert into asset_visual_variant(id,tenant_id,project_id,asset_type,asset_id,name,source_type,
                    generation_status,current_image_url,is_primary,created_by,created_at,updated_at)
                values (?,9001,52,'CHARACTER',?,'默认形态','MANUAL','SUCCESS',?,true,9001,now(),now())
                """, actor,actor,"/api/projects/52/ai-image-results/"+actor+"/thumbnail");
        }
        context.getBean(RbacService.class).initializeTenant(9001L);
        jdbc.update("insert into storyboard_batch(id,tenant_id,project_id,script_id,name,idempotency_key,created_by,created_at) values (1,9001,52,52,'85 Episode Batch','benchmark',9001,now())");
        for (int episode=1; episode<=85; episode++) {
            jdbc.update("""
                insert into script_episode(id,tenant_id,project_id,script_id,script_version_id,stable_key,
                    episode_no,title,content,content_fingerprint,reconciliation_status,status,created_at,updated_at)
                values (?,9001,52,52,1,?,?,?,? ,?,'MATCHED','ACTIVE',now(),now())
                """, (long)episode, "benchmark-"+episode, episode, "第"+episode+"集", "Episode body. ".repeat(1000), "hash-"+episode);
            var summary=json.createObjectNode().put("summary", "本集人物在房间内对话，随后走向窗边。".repeat(35)).put("endingHook","下一集继续。");
            summary.putArray("highlights").add("人物关系").add("窗边对话");
            context.getBean(ScriptEpisodeSummaryRepository.class).upsert(new ScriptEpisodeSummaryDocument(
                null,9001L,52L,52L,(long)episode,1,summary,"MANUAL",null,9001L,9001L,null,null));
            jdbc.update("""
                insert into ai_execution_task(id,tenant_id,user_id,project_id,scene,capability,business_type,
                    status,phase,progress,client_idempotency_key,trace_id,created_at,updated_at)
                values (?,9001,9001,52,'STORYBOARD_BREAKDOWN','TEXT','SCRIPT','SUCCEEDED','COMPLETED',100,?,?,now(),now())
                """, (long)episode, "benchmark-"+episode, "benchmark-"+episode);
            jdbc.update("insert into storyboard_batch_item(batch_id,tenant_id,project_id,episode_id,episode_no,execution_id,created_at) values (1,9001,52,?,?,?,now())",
                (long)episode,episode,(long)episode);
        }
        int[] nextShot = new int[86];
        for (int id=1;id<=851;id++) {
            int episode = id<=33 ? 1 : (id-34)%84+2;
            int no = ++nextShot[episode];
            var plan=json.createObjectNode().put("storyboardNo",no).put("durationSeconds",12).put("time","night").put("lighting","warm");
            plan.putArray("shots").addObject().put("shotNo",1).put("durationSeconds",12)
                .put("positioning","A character enters the room.").put("action","The character walks towards the window.");
            plan.putArray("warnings");
            var diagnostics=plan.putObject("diagnostics");
            diagnostics.put("normalizationCount",0).put("derivedSoundCount",0).put("businessCallCount",1).put("technicalRetryCount",0);
            diagnostics.putArray("classificationWarnings");diagnostics.putArray("actionWarnings");
            plan.put("rawProviderResponse","Internal generation trace. ".repeat(1300));
            var prompt=json.createObjectNode().put("version",2);
            var nodes=prompt.putArray("nodes");
            for (int mention=0;mention<32;mention++) {
                nodes.addObject().put("type","text").put("text","人物走向窗边，自然光线，动作与空间关系保持一致。");
                int actor=mention%2+1;
                nodes.addObject().put("type","mention").put("mediaType","IMAGE")
                    .put("sourceType","ASSET_VISUAL_VARIANT").put("sourceId",actor).put("assetType","CHARACTER")
                    .put("assetId",actor).put("variantId",actor).put("displayName","角色"+actor);
            }
            jdbc.update("""
                insert into storyboard(id,tenant_id,project_id,script_id,episode_id,episode_no,shot_no,
                    storyboard_no,visual_description,dialogue,duration_seconds,shot_plan_json,prompt_document_json,
                    video_prompt,status,created_by,created_at,updated_at)
                values (?,9001,52,52,?,?,?,?,'人物走进房间，在窗前停下。','人物：你好。',12,?,?,?,'CONFIRMED',9001,now(),now())
                """, (long)id,(long)episode,episode,no,no,plan.toString(),prompt.toString(),"镜头保持人物与场景一致，人物走向窗边，自然光线。".repeat(25));
        }
        for (int shot=1;shot<=33;shot++) for (int actor=1;actor<=2;actor++) {
            jdbc.update("""
                insert into storyboard_asset_reference(tenant_id,project_id,storyboard_id,asset_type,asset_id,
                    variant_id,reference_role,sort_order,resolution_status,source_type,source_name,created_by,created_at,updated_at)
                values (9001,52,?,'CHARACTER',?,?,'CHARACTER_REF',?,'RESOLVED','MANUAL',?,9001,now(),now())
                """, shot,actor,actor,actor,"角色"+actor);
        }
        var session=context.getBean(AuthSessionService.class).issue(context.getBean(UserMapper.class).selectById(9001L),"127.0.0.1","synthetic-benchmark");
        Path state=Path.of(".temp/storyboard-benchmark-session.json");Files.createDirectories(state.getParent());
        Files.writeString(state,json.writeValueAsString(Map.of("credential",session.credential(),"tenantId",9001,"projectId",52)));
        System.out.println("BENCHMARK_READY http://127.0.0.1:18189 project=52 episodes=85 storyboards=851 cache=disabled");
    }
}
