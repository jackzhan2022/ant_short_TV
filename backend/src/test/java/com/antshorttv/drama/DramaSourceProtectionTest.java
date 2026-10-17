package com.antshorttv.drama;

import com.antshorttv.common.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DramaSourceProtectionTest {
    DramaReferenceMapper references = mock(DramaReferenceMapper.class);
    DramaSourceProtection protection = new DramaSourceProtection(references);

    @Test void referencedSourceVersionCannotBeDeleted() throws Throwable {
        when(references.versionReferences(1L, 3L)).thenReturn(1L);
        ProceedingJoinPoint call = mock(ProceedingJoinPoint.class);
        assertThrows(BusinessException.class, () -> protection.deleteVersion(call, 1L, 2L, 3L));
        verify(references).lockVersion(1L, 2L, 3L);
        verify(call, never()).proceed();
    }

    @Test void unreferencedVersionContinuesDeletion() throws Throwable {
        ProceedingJoinPoint call = mock(ProceedingJoinPoint.class);
        protection.deleteVersion(call, 1L, 2L, 3L);
        verify(call).proceed();
    }

    @Test void activeLibraryOrPublishedObjectCannotBeDeleted() {
        when(references.objectReferences("fixed")).thenReturn(1L);
        assertThrows(BusinessException.class, () -> protection.beforeObjectDelete("fixed"));
        assertDoesNotThrow(() -> protection.beforeObjectDelete("unused"));
    }
}
