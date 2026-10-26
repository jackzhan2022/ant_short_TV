package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.project.ProjectAccessResolver;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class AssetNameAuthorizationTest {
    private ScriptWorkflowService workflow;
    private TenantContextResolver tenants;
    private ProjectAccessResolver projects;
    private ProjectPermissionGuard permissions;
    private AssetNormalizationGuard normalization;
    private AssetNameService names;
    @BeforeEach void setup() throws Exception {
        Map<Class<?>,Object> dependencies=new HashMap<>();
        var constructor=ScriptWorkflowService.class.getConstructors()[0];
        Object[] arguments=Arrays.stream(constructor.getParameterTypes())
            .map(type->dependencies.computeIfAbsent(type,key->mock(key,RETURNS_DEEP_STUBS))).toArray();
        workflow=(ScriptWorkflowService)constructor.newInstance(arguments);
        tenants=(TenantContextResolver)dependencies.get(TenantContextResolver.class);
        projects=(ProjectAccessResolver)dependencies.get(ProjectAccessResolver.class);
        permissions=(ProjectPermissionGuard)dependencies.get(ProjectPermissionGuard.class);
        normalization=mock(AssetNormalizationGuard.class);names=mock(AssetNameService.class);
        ReflectionTestUtils.setField(workflow,"normalizationGuard",normalization);
        ReflectionTestUtils.setField(workflow,"assetNameService",names);
        when(tenants.requireActiveMember(1L)).thenReturn(new TenantContext(9L,1L,1L,"OWNER"));
    }
    private void rename(boolean variant) {
        if(variant) workflow.renameVisualVariant(1L,2L,3L,"新名");
        else workflow.renameElement(1L,2L,"CHARACTER",3L,"新名");
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void retainsMembershipProjectPermissionAndNormalizationChecks(boolean variant) {
        rename(variant);
        var ordered=inOrder(tenants,projects,permissions,normalization,names);
        ordered.verify(tenants).requireActiveMember(1L);
        ordered.verify(projects).requireView(1L,2L);
        ordered.verify(permissions).require(1L,2L,"ELEMENT:EDIT");
        ordered.verify(normalization).requireReady(1L,2L);
        if(variant) ordered.verify(names).renameVariant(1L,2L,3L,"新名");
        else ordered.verify(names).renameAsset(1L,2L,"CHARACTER",3L,"新名");
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void deniesReadOnlyUsersBeforeMutation(boolean variant) {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN,"无编辑权限")).when(permissions).require(1L,2L,"ELEMENT:EDIT");
        assertThatThrownBy(()->rename(variant)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(names,normalization);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void rejectsBlockedNormalizationBeforeMutation(boolean variant) {
        doThrow(new BusinessException(ErrorCode.VALIDATION_ERROR,"归一未完成")).when(normalization).requireReady(1L,2L);
        assertThatThrownBy(()->rename(variant)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(names);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void rejectsProjectAccessBeforeMutation(boolean variant) {
        when(projects.requireView(1L,2L)).thenThrow(new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED,"无项目权限"));
        assertThatThrownBy(()->rename(variant)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(names,normalization,permissions);
    }
}
