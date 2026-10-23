package com.antshorttv.freecreation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.common.BusinessException;
import com.antshorttv.execution.*;
import com.antshorttv.project.ProjectAccessResolver;
import com.antshorttv.security.*;
import org.junit.jupiter.api.Test;

class FreeCreationExecutionAccessTest {
    @Test void membersCanViewTeamExecutionsButOnlyControlTheirOwn() {
        AiExecutionService executions = mock(AiExecutionService.class);
        TenantContextResolver tenants = mock(TenantContextResolver.class);
        AiExecutionTaskEntity task = new AiExecutionTaskEntity();
        task.id = 12L; task.tenantId = 1L; task.userId = 9L; task.businessType = "FREE_CREATION";
        when(executions.requireTask(12L)).thenReturn(task);
        when(tenants.requireActiveMember(1L)).thenReturn(new TenantContext(8L,1L,3L,"MEMBER"));
        var access = new AiExecutionAccessService(executions, tenants, mock(ProjectAccessResolver.class));
        assertThat(access.requireView(1L,12L)).isSameAs(task);
        assertThatThrownBy(() -> access.requireControl(1L,12L)).isInstanceOf(BusinessException.class);
        when(tenants.requireActiveMember(1L)).thenReturn(new TenantContext(9L,1L,4L,"MEMBER"));
        assertThat(access.requireControl(1L,12L)).isSameAs(task);
        when(tenants.requireActiveMember(2L)).thenReturn(new TenantContext(9L,2L,5L,"MEMBER"));
        assertThatThrownBy(() -> access.requireView(2L,12L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> access.requireControl(2L,12L)).isInstanceOf(BusinessException.class);
    }
}
