package com.antshorttv.freecreation;

import static org.mockito.Mockito.*;
import com.antshorttv.storage.ImageDisplayRenditionService;
import com.antshorttv.storage.MediaObjectIdentity;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties="media.free-creation-thumbnail.initial-delay-ms=3600000")
class FreeCreationThumbnailBackfillTest extends com.antshorttv.support.RegistrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired FreeCreationThumbnailBackfill backfill;
    @MockBean ImageDisplayRenditionService images;

    @Test void backfillsExistingImagesAndCoversWithoutReprocessingReadyThumbnailsOrExhaustedJobs() {
        original(1,"FREE_CREATION_OUTPUT","image/png");
        original(2,"FREE_CREATION_VIDEO_COVER","image/jpeg");
        original(3,"FREE_CREATION_OUTPUT","image/png");
        original(4,"FREE_CREATION_OUTPUT","image/png");
        original(5,"FREE_CREATION_OUTPUT","video/mp4");
        thumbnail(2,"FREE_CREATION_VIDEO_COVER","FAILED",1);
        thumbnail(3,"FREE_CREATION_OUTPUT","READY",1);
        thumbnail(4,"FREE_CREATION_OUTPUT","FAILED",3);
        backfill.submitMissing();
        verify(images).submitThumbnail(new MediaObjectIdentity(9910L,null,"FREE_CREATION_OUTPUT",1L,"v1"));
        verify(images).submitThumbnail(new MediaObjectIdentity(9910L,null,"FREE_CREATION_VIDEO_COVER",2L,"v1"));
        verifyNoMoreInteractions(images);
        thumbnail(1,"FREE_CREATION_OUTPUT","READY",1);
        jdbc.update("update media_processing_job set updated_at=now() where tenant_id=9910");
        clearInvocations(images);
        backfill.submitMissing();
        verifyNoInteractions(images);
    }

    private void original(long id,String type,String mime) {
        jdbc.update("""
            insert into media_object(tenant_id,asset_type,asset_id,version_id,rendition_type,object_key,mime_type,file_size,storage_class,status,created_at,updated_at)
            values (9910,?,?,'v1','ORIGINAL',?,?,1000,'STANDARD','READY',now(),now())
            """,type,id,"materials/9910/"+id+"/original.png",mime);
    }

    private void thumbnail(long id,String type,String status,int attempts) {
        String key="materials/9910/"+id+"/derived/thumbnail-480-v1.webp";
        jdbc.update("""
            insert into media_object(tenant_id,asset_type,asset_id,version_id,rendition_type,object_key,mime_type,file_size,storage_class,status,created_at,updated_at)
            values (9910,?,?,'v1','LIST_THUMBNAIL',?,'image/webp',0,'STANDARD',?,now(),now())
            """,type,id,key,status);
        Long mediaId=jdbc.queryForObject("select id from media_object where object_key=?",Long.class,key);
        jdbc.update("""
            insert into media_processing_job(tenant_id,media_object_id,operation,input_key,output_key,callback_token_hash,
                correlation_data,status,attempt_no,created_at,updated_at)
            values (9910,?,'LIST_THUMBNAIL',?,?,?,? ,?,?,now(),?)
            """,mediaId,"materials/9910/"+id+"/original.png",key,"thumbnail-test-"+id,"test",status,attempts,LocalDateTime.now().minusMinutes(15));
    }
}
