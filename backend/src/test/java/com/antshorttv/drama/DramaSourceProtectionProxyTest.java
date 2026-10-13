package com.antshorttv.drama;

import com.antshorttv.common.BusinessException;
import com.antshorttv.shot.ShotProductionService;
import com.antshorttv.storage.ObjectStorageService;
import com.qcloud.cos.COS;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class DramaSourceProtectionProxyTest {
    @Autowired ShotProductionService production;
    @Autowired ObjectStorageService storage;
    @MockBean DramaReferenceMapper references;
    @MockBean COS cos;
    @MockBean com.qcloud.cos.transfer.TransferManager transfers;

    @Test void realSpringProxyBlocksVersionDeletionAtActualEntryPoint() {
        assertTrue(AopUtils.isAopProxy(production));
        when(references.versionReferences(1L, 3L)).thenReturn(1L);
        BusinessException error = assertThrows(BusinessException.class,
            () -> production.deleteEpisodeVideoVersion(1L, 2L, 3L, null));
        assertTrue(error.getMessage().contains("独立剧库"));
        var order = inOrder(references);
        order.verify(references).lockVersion(1L, 2L, 3L);
        order.verify(references).versionReferences(1L, 3L);
        verifyNoInteractions(cos);
    }

    @Test void realStorageProxyBlocksReferencedObjectBeforeCosDelete() {
        assertTrue(AopUtils.isAopProxy(storage));
        when(references.objectReferences("materials/1/fixed/original.mp4")).thenReturn(1L);
        assertThrows(BusinessException.class, () -> storage.delete("materials/1/fixed/original.mp4"));
        verify(references).objectReferences("materials/1/fixed/original.mp4");
        verifyNoInteractions(cos);
    }

    @Test void realStorageProxyAllowsUnreferencedObjectToReachMockCos() {
        assertTrue(AopUtils.isAopProxy(storage));
        storage.delete("materials/1/unused/original.mp4");
        verify(references).objectReferences("materials/1/unused/original.mp4");
        verify(cos).deleteObject("antv-1418200553", "materials/1/unused/original.mp4");
    }
}
