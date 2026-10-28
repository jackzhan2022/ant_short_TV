package com.antshorttv.productiontask;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ProductionTaskReferenceReaderTest {
    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:h2:mem:task_refs_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));

    @Test
    void readsAllFrozenReferencesInOrderWithoutUsingTheCurrentAssetImageOrProviderUrls() {
        jdbc.execute("""
            create table ai_video_task_reference (id bigint, tenant_id bigint, project_id bigint, task_id bigint,
                media_type varchar(20), source_type varchar(40), source_id bigint, display_name varchar(100),
                compiled_label varchar(40), provider_role varchar(40), object_storage_path varchar(300),
                provider_url varchar(300), sort_order int)
            """);
        jdbc.execute("create table ai_image_result (id bigint,tenant_id bigint,project_id bigint,storage_path varchar(300),status varchar(20))");
        jdbc.update("insert into ai_image_result values (80,1,2,'materials/1/2/frozen.png','ACTIVE'),(81,1,2,'materials/1/2/new.png','ACTIVE')");
        jdbc.update("insert into ai_video_task_reference values (1,1,2,46,'IMAGE','ASSET_VISUAL_VARIANT',9,'角色形态','图片1','reference_image','materials/1/2/frozen.png','https://provider.example/private',1)");
        jdbc.update("insert into ai_video_task_reference values (2,1,2,46,'VIDEO','VIDEO_MATERIAL',10,'动作参考','视频1','reference_video','materials/1/2/motion.mp4',null,2)");
        jdbc.update("insert into ai_video_task_reference values (3,1,2,46,'AUDIO','PROJECT_AUDIO_ASSET',11,'角色音色','音频1','reference_audio','materials/1/2/voice.mp3',null,3)");
        jdbc.update("insert into ai_video_task_reference values (4,1,2,46,'IMAGE','ASSET_VISUAL_VARIANT',12,'已失效图片','图片2','reference_image',null,'https://provider.example/expired',4)");
        jdbc.update("insert into ai_video_task_reference values (5,9,2,46,'IMAGE','ASSET_VISUAL_VARIANT',13,'其他团队','图片3','reference_image','materials/9/2/secret.png',null,5)");
        var reader = new ProductionTaskReferenceReader(jdbc, new ObjectMapper());
        var items = reader.videoReferences(1L, 2L, 46L, "/api/projects/2/ai-image-results/80/download", null, null);
        assertThat(items).hasSize(4);
        assertThat(items).extracting(item -> item.get("displayName")).containsExactly("角色形态", "动作参考", "角色音色", "已失效图片");
        assertThat(items.get(0)).containsEntry("thumbnailUrl", "/api/projects/2/ai-image-results/80/thumbnail");
        assertThat(items.get(1)).containsEntry("url", "/materials/1/2/motion.mp4");
        assertThat(items.get(2)).containsEntry("url", "/materials/1/2/voice.mp3");
        assertThat(items.get(3)).doesNotContainKeys("url", "thumbnailUrl", "downloadUrl");
        assertThat(items.toString()).doesNotContain("provider.example", "new.png", "其他团队");
    }

    @Test
    void supportsNewlineAndJsonLegacyReferencesWithoutSilentlyTruncating() {
        var reader = new ProductionTaskReferenceReader(jdbc, new ObjectMapper());
        var newline = "/api/projects/2/ai-image-results/1/download\n/api/projects/2/ai-image-results/2/display";
        assertThat(reader.imageReferences(newline)).hasSize(2);
        assertThat(reader.imageReferences("[\"/api/projects/2/ai-image-results/1/download\",{\"url\":\"/api/projects/2/ai-image-results/2/display\"},\"https://outside.example/private\"]"))
            .hasSize(2);
        var many = java.util.stream.IntStream.rangeClosed(1, 23)
            .mapToObj(id -> "/api/projects/2/ai-image-results/" + id + "/thumbnail")
            .collect(java.util.stream.Collectors.joining("\n"));
        assertThat(reader.imageReferences(many)).hasSize(23);
    }
}
