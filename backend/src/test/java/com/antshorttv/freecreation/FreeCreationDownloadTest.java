package com.antshorttv.freecreation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.antshorttv.common.BusinessException;
import com.antshorttv.execution.*;
import com.antshorttv.security.*;
import com.antshorttv.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FreeCreationDownloadTest {
    FreeCreationMapper creations=mock(FreeCreationMapper.class);
    FreeCreationMediaMapper outputs=mock(FreeCreationMediaMapper.class);
    ObjectStorageService storage=mock(ObjectStorageService.class);
    AiExecutionService executions=mock(AiExecutionService.class);
    TenantContextResolver tenants=mock(TenantContextResolver.class);
    FreeCreationMediaService media=mock(FreeCreationMediaService.class);
    FreeCreationService service;
    FreeCreationEntity creation;
    FreeCreationMediaEntity output;
    AiExecutionTaskEntity task;
    byte[] bytes={1,2,3};

    @BeforeEach void setup() {
        service=new FreeCreationService(creations,outputs,media,tenants,null,executions,null,null,storage,null,null,null);
        when(tenants.requireActiveMember(1L)).thenReturn(new TenantContext(2L,1L,3L,"MEMBER"));
        creation=new FreeCreationEntity();creation.id=14L;creation.tenantId=1L;creation.executionId=10L;
        when(creations.selectById(14L)).thenReturn(creation);
        task=new AiExecutionTaskEntity();task.status="SUCCEEDED";when(executions.requireTask(10L)).thenReturn(task);
        output=new FreeCreationMediaEntity();output.id=20L;output.tenantId=1L;output.creationId=14L;output.role="OUTPUT";
        output.fileName="作品.png";output.mimeType="image/png";output.fileSize=3L;
        output.objectKey="materials/1/free-creations/202610/14/output-0/original.png";
        when(outputs.selectById(20L)).thenReturn(output);
        when(storage.resource(output.objectKey)).thenReturn(new ByteArrayResource(bytes));
    }

    @Test void sendsOriginalBytesAsAnAttachmentWithoutRedirectingToCdn() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new FreeCreationController(service)).build();
        mvc.perform(get("/api/free-creations/14/outputs/20/download-file").header("X-Tenant-Id",1))
            .andExpect(status().isOk()).andExpect(content().bytes(bytes)).andExpect(content().contentType("image/png"))
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("attachment;")))
            .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("filename*=UTF-8''")))
            .andExpect(header().doesNotExist("Location"));
        verify(storage).resource(output.objectKey);verifyNoInteractions(media);
    }

    @Test void supportsVideoOriginals() {
        output.mimeType="video/mp4";output.fileName="video.mp4";
        var file=service.downloadFile(1L,14L,20L);
        assertThat(file.fileName()).isEqualTo("video.mp4");assertThat(file.contentType()).isEqualTo("video/mp4");
    }

    @Test void refusesOutputsFromAnotherTenantOrCreationAndReferencesBeforeOpeningStorage() {
        output.tenantId=2L;assertThatThrownBy(()->service.downloadFile(1L,14L,20L)).isInstanceOf(BusinessException.class);
        output.tenantId=1L;output.creationId=99L;assertThatThrownBy(()->service.downloadFile(1L,14L,20L)).isInstanceOf(BusinessException.class);
        output.creationId=14L;output.role="REFERENCE";assertThatThrownBy(()->service.downloadFile(1L,14L,20L)).isInstanceOf(BusinessException.class);
        verify(storage,never()).resource(any());
    }

    @Test void refusesUnpublishedOrForeignCreationsBeforeOpeningStorage() {
        task.status="RUNNING";assertThatThrownBy(()->service.downloadFile(1L,14L,20L)).isInstanceOf(BusinessException.class);
        task.status="SUCCEEDED";creation.tenantId=2L;
        assertThatThrownBy(()->service.downloadFile(1L,14L,20L)).isInstanceOf(BusinessException.class);
        verify(storage,never()).resource(any());
    }
}
