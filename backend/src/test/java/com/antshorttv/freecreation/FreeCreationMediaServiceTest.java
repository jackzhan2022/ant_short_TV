package com.antshorttv.freecreation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.common.BusinessException;
import com.antshorttv.security.TenantContext;
import com.antshorttv.storage.*;
import com.qcloud.cos.COS;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;

class FreeCreationMediaServiceTest {
    final FreeCreationMediaMapper media=mock(FreeCreationMediaMapper.class);
    final FreeCreationMapper creations=mock(FreeCreationMapper.class);
    final MediaUploadSessionService uploads=mock(MediaUploadSessionService.class);
    final ObjectStorageService storage=mock(ObjectStorageService.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final FreeCreationMediaService service=new FreeCreationMediaService(media,creations,uploads,storage,
        mock(ObjectStorageKeyFactory.class),mock(ImageDisplayRenditionService.class),mock(MediaDeliveryGrantService.class),
        mock(COS.class),mock(ObjectStorageProperties.class),jdbc,mock(CloudInfiniteVideoCoverService.class),
        mock(com.antshorttv.execution.AiExecutionService.class),mock(org.springframework.transaction.PlatformTransactionManager.class));

    @Test void rejectsDeclaredMimeThatDoesNotMatchTheVerifiedImageBytes() throws Exception {
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(320,320,BufferedImage.TYPE_INT_RGB),"png",bytes);
        when(uploads.requireCompleted(2L,1L,null,"upload")).thenReturn(new VerifiedMediaUpload("upload","objects/test.jpg","image/jpeg",bytes.size(),"etag"));
        when(storage.resource("objects/test.jpg")).thenReturn(new ByteArrayResource(bytes.toByteArray()));
        when(jdbc.queryForObject("select file_name from media_upload_session where session_token=?",String.class,"upload")).thenReturn("test.jpg");
        assertThatThrownBy(()->service.reference(new TenantContext(2L,1L,3L,"MEMBER"),new FreeCreationRequest.Reference("IMAGE","upload",null)))
            .isInstanceOf(BusinessException.class).hasMessageContaining("实际格式");
        verify(uploads).requireCompleted(2L,1L,null,"upload");
    }

    @Test void rejectsCrossTenantReferenceEvenIfItsIdentifierIsKnown() {
        var item=new FreeCreationMediaEntity();item.id=20L;item.tenantId=7L;item.creationId=8L;item.kind="IMAGE";
        var owner=new FreeCreationEntity();owner.id=8L;owner.tenantId=7L;
        when(media.selectById(20L)).thenReturn(item);when(creations.selectById(8L)).thenReturn(owner);
        assertThatThrownBy(()->service.reference(new TenantContext(2L,1L,3L,"MEMBER"),new FreeCreationRequest.Reference("IMAGE",null,20L)))
            .isInstanceOf(BusinessException.class).hasMessageContaining("不存在");
        verifyNoInteractions(uploads,storage);
    }

    @Test void rejectsBothReferenceSourcesAndPreservesFractionalVideoFrameRates() {
        assertThatThrownBy(()->service.reference(new TenantContext(2L,1L,3L,"MEMBER"),new FreeCreationRequest.Reference("IMAGE","upload",20L)))
            .isInstanceOf(BusinessException.class);
        assertThat(FreeCreationMediaService.decimalRate("30000/1001")).isEqualByComparingTo("29.9700");
        try(var validator=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var refs=java.util.Collections.nCopies(30,new FreeCreationRequest.Reference("IMAGE",null,20L));
            assertThat(validator.getValidator().validate(new FreeCreationRequest(null,"VIDEO",1L,"海面日出",java.util.Map.of(),refs))).isEmpty();
        }
    }

    @Test void imageEstimatesValidateUploadOwnershipWithoutDownloadingTheImage() {
        when(uploads.requireCompleted(2L,1L,null,"upload"))
            .thenReturn(new VerifiedMediaUpload("upload","objects/test.png","image/png",100,"etag"));
        when(jdbc.queryForObject("select file_name from media_upload_session where session_token=?",String.class,"upload"))
            .thenReturn("test.png");
        var context=new TenantContext(2L,1L,3L,"MEMBER");
        var reference=new FreeCreationRequest.Reference("IMAGE","upload",null);
        var item=service.referenceForImageEstimate(context,reference);
        assertThat(item.objectKey).isEqualTo("objects/test.png");
        assertThat(item.width).isNull();
        assertThat(item.role).isEqualTo("REFERENCE");
        verifyNoInteractions(storage,jdbc);
        when(uploads.requireCompleted(2L,1L,null,"upload")).thenThrow(new IllegalArgumentException("expired"));
        assertThatThrownBy(()->service.referenceForImageEstimate(context,reference)).isInstanceOf(BusinessException.class);
        verify(uploads,times(2)).requireCompleted(2L,1L,null,"upload");
    }

    @Test void immutableUploadMetadataIsReusedButEveryRequestRechecksAccessAndNewEtagsAreInspected() throws Exception {
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(320,240,BufferedImage.TYPE_INT_RGB),"png",bytes);
        when(uploads.requireCompleted(2L,1L,null,"upload"))
            .thenReturn(new VerifiedMediaUpload("upload","objects/test.png","image/png",bytes.size(),"etag"));
        when(storage.resource("objects/test.png")).thenReturn(new ByteArrayResource(bytes.toByteArray()));
        when(jdbc.queryForObject("select file_name from media_upload_session where session_token=?",String.class,"upload"))
            .thenReturn("test.png");
        var context=new TenantContext(2L,1L,3L,"MEMBER");
        var reference=new FreeCreationRequest.Reference("IMAGE","upload",null);
        for(int i=0;i<10;i++) {
            var item=service.reference(context,reference);
            assertThat(item.width).isEqualTo(320);assertThat(item.height).isEqualTo(240);
            item.width=1;
        }
        verify(storage,times(1)).resource("objects/test.png");
        verify(uploads,times(10)).requireCompleted(2L,1L,null,"upload");
        when(uploads.requireCompleted(2L,1L,null,"upload"))
            .thenReturn(new VerifiedMediaUpload("upload","objects/test.png","image/png",bytes.size(),"changed-etag"));
        service.reference(context,reference);
        verify(storage,times(2)).resource("objects/test.png");
        when(uploads.requireCompleted(2L,1L,null,"upload")).thenThrow(new IllegalArgumentException("expired"));
        assertThatThrownBy(()->service.reference(context,reference)).isInstanceOf(BusinessException.class);
        verify(storage,times(2)).resource("objects/test.png");
    }

    @Test void repeatedReadOnlyEstimatesDoNotMutateTheMapperCachedReference() {
        var item=new FreeCreationMediaEntity();item.id=20L;item.tenantId=1L;item.creationId=8L;item.kind="IMAGE";item.role="REFERENCE";
        var owner=new FreeCreationEntity();owner.id=8L;owner.tenantId=1L;
        when(media.selectById(20L)).thenReturn(item);when(creations.selectById(8L)).thenReturn(owner);
        var context=new TenantContext(2L,1L,3L,"MEMBER");
        var reference=new FreeCreationRequest.Reference("IMAGE",null,20L);
        assertThat(service.referenceForImageEstimate(context,reference).id).isNull();
        assertThat(service.referenceForImageEstimate(context,reference).id).isNull();
        assertThat(item.id).isEqualTo(20L);assertThat(item.creationId).isEqualTo(8L);
        verify(creations,times(2)).selectById(8L);
        verifyNoInteractions(storage,uploads);
    }
}
