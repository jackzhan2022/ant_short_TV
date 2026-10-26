package com.antshorttv.video;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.antshorttv.common.BusinessException;
import com.antshorttv.storage.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class VideoFrameCaptureServiceTest {
    @Autowired VideoFrameCaptureService service;
    @Autowired JdbcTemplate jdbc;
    @MockBean MediaUploadSessionService uploads;
    @MockBean ObjectStorageService storage;
    @MockBean ImageDisplayRenditionService renditions;
    byte[] png;

    @BeforeEach void seed() throws Exception {
        jdbc.update("""
            insert into storyboard (id,tenant_id,project_id,episode_no,shot_no,visual_description,status,created_by,created_at,updated_at)
            values (92401,92001,92002,1,2,'相遇','DRAFT',99,now(),now())
            """);
        jdbc.update("""
            insert into ai_video_task (id,tenant_id,project_id,storyboard_id,provider_code,model,prompt,duration_seconds,aspect_ratio,status,created_by,created_at,updated_at)
            values (92501,92001,92002,92401,'mock','mock','test',5,'16:9','SUCCEEDED',99,now(),now())
            """);
        jdbc.update("""
            insert into ai_video_result (id,tenant_id,project_id,task_id,storyboard_id,video_url,storage_path,duration_seconds,is_selected,status,created_at,updated_at)
            values (92601,92001,92002,92501,92401,'/video','materials/92001/92002/video.mp4',5,true,'ACTIVE',now(),now())
            """);
        jdbc.update("""
            insert into media_upload_session (tenant_id,project_id,user_id,session_token,object_key,file_name,content_type,status,expires_at,created_at,updated_at)
            values (92001,92002,99,'frame-token','frame-upload','frame.png','image/png','COMPLETED',dateadd('DAY',1,current_timestamp),now(),now())
            """);
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20,30,BufferedImage.TYPE_INT_RGB),"png",output);
        png = output.toByteArray();
        verified(png,"image/png");
        when(storage.copyCompletedUploadOriginal(any(),anyString())).thenAnswer(call ->
            new StoredObject(call.getArgument(1),png.length,"image/png","etag","STANDARD"));
        when(renditions.registerOriginalAndSubmit(any(),any(),anyInt(),anyInt(),anyString()))
            .thenReturn(new RegisteredImageDisplay("original","display","PENDING"));
    }
    void verified(byte[] bytes,String mime) {
        when(uploads.requireCompleted(99L,92001L,92002L,"frame-token"))
            .thenReturn(new VerifiedMediaUpload("frame-token","frame-upload",mime,bytes.length,"etag"));
        when(storage.resource("frame-upload")).thenReturn(new ByteArrayResource(bytes));
    }
    VideoFrameCaptureService.FrameResponse save(double time) {
        return service.save(92001L,92002L,92601L,99L,"frame-token",time);
    }
    @Test void savesOriginalPngWithSourceMetadataAndRenditions() {
        var result=save(1.25);
        assertThat(result.sourceVideoResultId()).isEqualTo(92601L);
        assertThat(result.timeSeconds()).isEqualTo(1.25);
        assertThat(result.imageUrl()).isEqualTo("/api/projects/92002/ai-image-results/"+result.id()+"/display");
        assertThat(jdbc.queryForMap("select target_type,width,height,task_id from ai_image_result where id=?",result.id()))
            .containsEntry("target_type","VIDEO_FRAME").containsEntry("width",20).containsEntry("height",30).containsEntry("task_id",null);
        assertThat(jdbc.queryForObject("select storyboard_id from video_frame_capture where image_result_id=?",Long.class,result.id())).isEqualTo(92401L);
        verify(renditions).registerOriginalAndSubmit(any(),any(),eq(20),eq(30),anyString());
    }
    @Test void reusesSameUploadSessionAndRejectsChangedTimestamp() {
        var first=save(1.25);
        assertThat(save(1.25)).isEqualTo(first);
        assertThatThrownBy(() -> save(2)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select count(*) from video_frame_capture where session_token='frame-token'",Long.class)).isEqualTo(1L);
        verify(renditions,times(1)).registerOriginalAndSubmit(any(),any(),anyInt(),anyInt(),anyString());
    }
    @Test void rejectsOtherTenantProjectDeletedStoryboardAndUnownedUpload() {
        assertThat(service.sourceTenantId(92002L,92601L)).isEqualTo(92001L);
        assertThatThrownBy(() -> service.sourceTenantId(123L,92601L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.source(123L,92002L,92601L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.source(92001L,123L,92601L)).isInstanceOf(BusinessException.class);
        when(uploads.requireCompleted(99L,92001L,92002L,"frame-token")).thenThrow(new IllegalArgumentException("ownership"));
        assertThatThrownBy(() -> save(1)).hasMessageContaining("ownership");
        verify(storage,never()).resource(anyString());
        jdbc.update("update storyboard set deleted_at=now() where id=92401");
        assertThatThrownBy(() -> service.source(92001L,92002L,92601L)).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsInvalidTimesAndNonPngContent() {
        for (double time:new double[]{-1,6,Double.NaN,Double.POSITIVE_INFINITY})
            assertThatThrownBy(() -> save(time)).isInstanceOf(BusinessException.class);
        verified("corrupt".getBytes(),"image/png");
        assertThatThrownBy(() -> save(1)).isInstanceOf(BusinessException.class);
        verified(png,"image/jpeg");
        assertThatThrownBy(() -> save(1)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(renditions);
    }
    @Test void rejectsOversizedAndDecompressionBombPngBeforeRegistration() {
        when(uploads.requireCompleted(99L,92001L,92002L,"frame-token"))
            .thenReturn(new VerifiedMediaUpload("frame-token","frame-upload","image/png",21*1024*1024,"etag"));
        assertThatThrownBy(() -> save(1)).isInstanceOf(BusinessException.class);
        verify(storage,never()).resource(anyString());
        byte[] oversized=png.clone();
        java.nio.ByteBuffer.wrap(oversized).putInt(16,100000).putInt(20,100000);
        var crc=new java.util.zip.CRC32(); crc.update(oversized,12,17);
        java.nio.ByteBuffer.wrap(oversized).putInt(29,(int)crc.getValue());
        verified(oversized,"image/png");
        assertThatThrownBy(() -> save(1)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(renditions);
    }

    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentRetriesCreateOnlyOneImage() throws Exception {
        var bothReady=new java.util.concurrent.CountDownLatch(2);
        when(uploads.requireCompleted(99L,92001L,92002L,"frame-token")).thenAnswer(call -> {
            bothReady.countDown();
            if(!bothReady.await(10,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("parallel timeout");
            return new VerifiedMediaUpload("frame-token","frame-upload","image/png",png.length,"etag");
        });
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(() -> save(1.25));
            var second=pool.submit(() -> save(1.25));
            assertThat(first.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(second.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertThat(jdbc.queryForObject("select count(*) from video_frame_capture where session_token='frame-token'",Long.class)).isEqualTo(1L);
            verify(renditions,times(1)).registerOriginalAndSubmit(any(),any(),anyInt(),anyInt(),anyString());
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS);
            jdbc.update("delete from video_frame_capture where tenant_id=92001");
            jdbc.update("delete from ai_image_result where tenant_id=92001");
            jdbc.update("delete from media_upload_session where tenant_id=92001");
            jdbc.update("delete from ai_video_result where tenant_id=92001");
            jdbc.update("delete from ai_video_task where tenant_id=92001");
            jdbc.update("delete from storyboard where tenant_id=92001");
        }
    }

    @Test void refusesCrossProjectAndUrlStoragePaths() {
        jdbc.update("update ai_video_result set storage_path='materials/123/456/video.mp4' where id=92601");
        assertThatThrownBy(() -> service.source(92001L,92002L,92601L)).isInstanceOf(BusinessException.class);
        jdbc.update("update ai_video_result set storage_path='https://untrusted.example/video.mp4' where id=92601");
        assertThatThrownBy(() -> service.source(92001L,92002L,92601L)).isInstanceOf(BusinessException.class);
    }}
