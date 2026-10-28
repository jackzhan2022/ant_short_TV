package com.antshorttv.freecreation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.security.TenantContext;
import com.antshorttv.storage.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class FreeCreationMediaBatchTest {
    private final TenantContext context=new TenantContext(2L,1L,3L,"OWNER");
    private final ImageDisplayRenditionService images=mock(ImageDisplayRenditionService.class);
    private final MediaDeliveryGrantService grants=mock(MediaDeliveryGrantService.class);
    private JdbcTemplate jdbc;
    private FreeCreationMediaService service;
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("create table media_object(tenant_id bigint,project_id bigint,asset_type varchar(80),asset_id bigint,version_id varchar(80),rendition_type varchar(80),object_key varchar(200),status varchar(32))");
        service=new FreeCreationMediaService(mock(FreeCreationMediaMapper.class),mock(FreeCreationMapper.class),mock(MediaUploadSessionService.class),mock(ObjectStorageService.class),mock(ObjectStorageKeyFactory.class),images,grants,mock(com.qcloud.cos.COS.class),mock(ObjectStorageProperties.class),jdbc,mock(CloudInfiniteVideoCoverService.class),mock(com.antshorttv.execution.AiExecutionService.class),mock(org.springframework.transaction.PlatformTransactionManager.class),mock(ProjectImageReferenceService.class));
        when(grants.issueAll(anyList())).thenAnswer(call->{
            Map<String,DeliveryGrant> result=new HashMap<>();
            for(DeliveryGrantRequest r: (List<DeliveryGrantRequest>)call.getArgument(0))result.put(r.objectKey(),new DeliveryGrant("https://cdn.example/"+r.objectKey(),Instant.MAX));
            return result;
        });
    }
    @AfterEach void close() {jdbc.execute("shutdown");}
    private FreeCreationMediaEntity item(long id,String role,String kind,String thumbnail) {
        var item=new FreeCreationMediaEntity();item.id=id;item.tenantId=1L;item.creationId=10L;item.position=(int)id;item.role=role;item.kind=kind;item.fileName=kind+id;item.objectKey="original-"+id;item.thumbnailKey=thumbnail;return item;
    }
    private void rendition(long tenant,long id,String type,String rendition,String key,String status) {
        jdbc.update("insert into media_object values(?,null,?,10,?,?,?,?)",tenant,type,"output-"+id,rendition,key,status);
    }
    @Test void presentsAllKindsWithOneGrantBatchAndNoIndividualRenditionLookups() {
        var image=item(1,"OUTPUT","IMAGE","old-cover");var video=item(2,"OUTPUT","VIDEO","video-cover");
        var reference=item(3,"REFERENCE","IMAGE",null);var audio=item(4,"REFERENCE","AUDIO",null);
        rendition(1,1,"FREE_CREATION_OUTPUT","DISPLAY_IMAGE_SLIM","display-image","READY");
        rendition(1,1,"FREE_CREATION_OUTPUT","LIST_THUMBNAIL","image-thumb","READY");
        rendition(1,2,"FREE_CREATION_VIDEO_COVER","LIST_THUMBNAIL","video-thumb","READY");
        rendition(9,1,"FREE_CREATION_OUTPUT","LIST_THUMBNAIL","other-tenant","READY");
        var result=service.presentAll(context,List.of(image,video,reference,audio));
        assertThat(result.get(1L)).containsEntry("previewUrl","https://cdn.example/display-image").containsEntry("thumbnailUrl","https://cdn.example/image-thumb").containsEntry("thumbnailStatus","READY");
        assertThat(result.get(2L)).containsEntry("previewUrl","https://cdn.example/original-2").containsEntry("thumbnailUrl","https://cdn.example/video-thumb");
        assertThat(result.get(3L)).containsEntry("previewUrl","https://cdn.example/original-3").containsEntry("thumbnailUrl","https://cdn.example/original-3");
        assertThat(result.get(4L)).containsEntry("thumbnailUrl",null);
        verify(grants).issueAll(anyList());verify(grants,never()).issue(any());verifyNoInteractions(images);
    }
    @Test void preservesPendingAndFailedThumbnailStatusWithoutReturningBrokenUrls() {
        rendition(1,1,"FREE_CREATION_OUTPUT","LIST_THUMBNAIL","broken-thumb","FAILED");
        var result=service.presentAll(context,List.of(item(1,"OUTPUT","IMAGE",null),item(2,"OUTPUT","VIDEO",null)));
        assertThat(result.get(1L)).containsEntry("thumbnailStatus","FAILED").containsEntry("thumbnailUrl",null);
        assertThat(result.get(2L)).containsEntry("thumbnailStatus","PENDING").containsEntry("thumbnailUrl",null);
    }
    @Test void validatesEveryTenantBeforeGrantingAnyMedia() {
        var foreign=item(2,"OUTPUT","VIDEO",null);foreign.tenantId=9L;
        assertThatThrownBy(()->service.presentAll(context,List.of(item(1,"OUTPUT","IMAGE",null),foreign)))
            .isInstanceOf(com.antshorttv.common.BusinessException.class);
        verifyNoInteractions(grants);
    }
    @Test void emptyBatchDoesNotReadOrIssueGrants() { assertThat(service.presentAll(context,List.of())).isEmpty();verifyNoInteractions(grants,images); }
}
