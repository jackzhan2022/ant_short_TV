package com.antshorttv.downloadcenter;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
class DownloadFileNamesTest {
    JdbcTemplate jdbc;DownloadFileNames names;
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:names"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));
        names=new DownloadFileNames(jdbc);
        jdbc.execute("create table project(id bigint,tenant_id bigint,name varchar(200))");jdbc.execute("insert into project values(2,1,'星河')");
    }
    @Test void includesAssetAndVariantNamesInsteadOfStorageIds() {
        jdbc.execute("create table ai_image_result(id bigint,tenant_id bigint,project_id bigint,target_type varchar(30),target_id bigint)");
        jdbc.execute("create table asset_visual_variant(id bigint,tenant_id bigint,project_id bigint,asset_type varchar(30),asset_id bigint,name varchar(100))");
        jdbc.execute("create table character_asset(id bigint,tenant_id bigint,project_id bigint,name varchar(100))");
        jdbc.execute("insert into ai_image_result values(9,1,2,'VISUAL_VARIANT',5)");jdbc.execute("insert into asset_visual_variant values(5,1,2,'CHARACTER',6,'冬装')");jdbc.execute("insert into character_asset values(6,1,2,'林川')");
        assertThat(names.name(1L,new DownloadTask.Source("IMAGE",2L,9L,null),"original.png")).isEqualTo("星河_林川_冬装_图片_9.png");
    }
    @Test void usesEpisodeAndVersionForFinishedVideos() {
        jdbc.execute("create table episode_video_version(id bigint,tenant_id bigint,project_id bigint,episode_no int,version_no int)");jdbc.execute("insert into episode_video_version values(9,1,2,3,2)");
        assertThat(names.name(1L,new DownloadTask.Source("EPISODE_VERSION",2L,9L,null),"original.mp4")).isEqualTo("星河_第03集_成片_v2_9.mp4");
    }
    @Test void freeCreationUsesShortDescriptionAndRetainsReferenceNames() {
        jdbc.execute("create table free_creation(id bigint,tenant_id bigint,media_type varchar(30),prompt varchar(300))");jdbc.execute("insert into free_creation values(3,1,'IMAGE','小猫在月球上漫步')");
        assertThat(names.name(1L,new DownloadTask.Source("FREE_CREATION",null,9L,3L),"provider-uuid.png")).isEqualTo("自由创作_图片_小猫在月球上漫步_作品3_9.png");
        assertThat(names.name(1L,new DownloadTask.Source("FREE_REFERENCE",null,9L,3L),"角色参考.png")).isEqualTo("自由创作_参考素材_角色参考_作品3_9.png");
    }
}
