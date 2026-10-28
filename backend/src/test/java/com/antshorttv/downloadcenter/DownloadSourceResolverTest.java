package com.antshorttv.downloadcenter;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.aiimage.AiImageDownloadService;
import com.antshorttv.common.*;
import com.antshorttv.freecreation.FreeCreationService;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.review.*;
import com.antshorttv.storage.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DownloadSourceResolverTest {
    JdbcTemplate jdbc;ProjectPermissionGuard permissions;AiImageDownloadService images;ObjectStorageService storage;DownloadSourceResolver resolver;
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:sources"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("create table project(id bigint,tenant_id bigint,name varchar(200))");
        jdbc.execute("insert into project values(2,1,'星河')");
        jdbc.execute("create table ai_image_result(id bigint,tenant_id bigint,project_id bigint,target_type varchar(30),target_id bigint)");
        permissions=mock(ProjectPermissionGuard.class);images=mock(AiImageDownloadService.class);storage=mock(ObjectStorageService.class);
        resolver=new DownloadSourceResolver(jdbc,permissions,images,mock(FreeCreationService.class),mock(ReviewReadService.class),mock(ReviewWorkbenchService.class),storage,new ObjectStorageKeyFactory());
    }
    @Test void metadataValidationDoesNotFetchRemoteFiles() {
        when(images.describe(2L,3L)).thenReturn(new AiImageDownloadService.Original("materials/1/2/image.png","图片.png","image/png",10L));
        assertThat(resolver.resolve(1L,List.of(new DownloadTask.Source("IMAGE",2L,3L,null)))).hasSize(1);
        verify(permissions).require(1L,2L,"AI_IMAGE_TASK:VIEW");verifyNoInteractions(storage);
    }
    @Test void deniesForbiddenSourcesBeforeAccessingStorage() {
        when(permissions.require(1L,2L,"AI_IMAGE_TASK:VIEW")).thenThrow(new BusinessException(ErrorCode.FORBIDDEN,"无权访问"));
        assertThatThrownBy(()->resolver.resolve(1L,List.of(new DownloadTask.Source("IMAGE",2L,3L,null)))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(images,storage);
    }
    @Test void preventsStoragePathsFromOtherProjectsAndRemoteUrls() {
        jdbc.execute("create table ai_video_result(id bigint,tenant_id bigint,project_id bigint,storage_path varchar(200),status varchar(20))");
        for(String key:List.of("materials/1/99/video.mp4","https://example.com/video.mp4","materials/1/2/../video.mp4")) {
            jdbc.update("delete from ai_video_result");jdbc.update("insert into ai_video_result values(3,1,2,?,'ACTIVE')",key);
            assertThatThrownBy(()->resolver.resolve(1L,List.of(new DownloadTask.Source("VIDEO",2L,3L,null)))).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(storage);
    }
    @Test void crossProjectReferencesAlsoRequireAccessToTheirOrigin() {
        jdbc.execute("create table ai_video_task_reference(id bigint,tenant_id bigint,project_id bigint,object_storage_path varchar(200),storyboard_id bigint,display_name varchar(100))");
        jdbc.update("insert into ai_video_task_reference values(3,1,2,'materials/1/99/reference.mp4',4,'动作参考')");
        jdbc.execute("create table storyboard(id bigint,tenant_id bigint,project_id bigint,episode_no int,shot_no int)");
        var source=List.of(new DownloadTask.Source("VIDEO_REFERENCE",2L,3L,null));
        assertThat(resolver.resolve(1L,source)).hasSize(1);
        verify(permissions).require(1L,99L,"PROJECT:VIEW");
        when(permissions.require(1L,99L,"PROJECT:VIEW")).thenThrow(new BusinessException(ErrorCode.FORBIDDEN,"无权访问"));
        assertThatThrownBy(()->resolver.resolve(1L,source)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(storage);
    }
    @Test void namesVideosWithProjectEpisodeShotAndStableResultId() {
        jdbc.execute("create table storyboard(id bigint,tenant_id bigint,project_id bigint,episode_no int,shot_no int)");
        jdbc.execute("insert into storyboard values(7,1,2,1,3)");
        jdbc.execute("create table ai_video_result(id bigint,tenant_id bigint,project_id bigint,storyboard_id bigint,storage_path varchar(200),status varchar(20))");
        jdbc.execute("insert into ai_video_result values(9,1,2,7,'materials/1/2/original.mp4','ACTIVE')");
        var file=resolver.resolve(1L,List.of(new DownloadTask.Source("VIDEO",2L,9L,null))).get(0);
        assertThat(file.name()).isEqualTo("星河_第01集_分镜003_视频_9.mp4");
        assertThat(file.objectKey()).isEqualTo("materials/1/2/original.mp4");verifyNoInteractions(storage);
    }
    @Test void normalizesEpisodeToFixedResultIdsAndPrefersSelectedComposition() {
        jdbc.execute("create table storyboard(id bigint,tenant_id bigint,project_id bigint,episode_no int,shot_no int,deleted_at timestamp,current_shot_video_url varchar(100),current_shot_result_id bigint,current_video_result_id bigint)");
        jdbc.execute("create table ai_video_result(id bigint,tenant_id bigint,project_id bigint,storyboard_id bigint,status varchar(20))");
        jdbc.execute("create table shot_compose_result(id bigint,tenant_id bigint,project_id bigint,storyboard_id bigint,status varchar(20))");
        jdbc.update("insert into storyboard values(3,1,2,1,1,null,'/composed',5,4)");
        jdbc.update("insert into ai_video_result values(4,1,2,3,'ACTIVE')");jdbc.update("insert into shot_compose_result values(5,1,2,3,'ACTIVE')");
        var normalized=resolver.normalize(1L,List.of(new DownloadTask.Source("EPISODE_STORYBOARDS",2L,1L,null)));
        assertThat(normalized).containsExactly(new DownloadTask.Source("COMPOSE",2L,5L,null));
        jdbc.update("update storyboard set current_shot_result_id=9");assertThat(normalized.get(0).id()).isEqualTo(5);
    }
}
