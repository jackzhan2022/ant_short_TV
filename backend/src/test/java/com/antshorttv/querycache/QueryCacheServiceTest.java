package com.antshorttv.querycache;

import com.antshorttv.ai.*;
import com.antshorttv.authsession.AuthenticatedUser;
import com.antshorttv.messagecenter.MessageCenterService;
import com.antshorttv.platform.PlatformAuthorizationService;
import com.antshorttv.productiontask.*;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectMapper;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.*;
import com.antshorttv.operationlog.OperationLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QueryCacheServiceTest {
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final QueryResultCache cache=new QueryResultCache(new QueryResultCacheTest.Store(),mapper,
        new AiSecretCodec("test-key"),new SimpleMeterRegistry(),true);
    @Test void unreadSqlExecutesOnceButPermissionIsRecheckedAndUsersStayIsolated() {
        var jdbc=spy(new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:cache_messages;DB_CLOSE_DELAY=-1","sa","")));
        jdbc.execute("create table inbox_message(id varchar(36),category varchar(32))");
        jdbc.execute("create table message_recipient(message_id varchar(36),user_id bigint,deleted_at timestamp,read_at timestamp)");
        jdbc.update("insert into inbox_message values ('m','PRODUCT')");
        jdbc.update("insert into message_recipient values ('m',1,null,null)");
        var principal=mock(CurrentPrincipal.class);var permissions=mock(PlatformAuthorizationService.class);
        when(principal.require()).thenReturn(new AuthenticatedUser(1L,"mobile","session",LocalDateTime.now().plusDays(1)));
        when(permissions.roleCodes(1L)).thenReturn(List.of("PLATFORM_ADMIN"));
        var service=new MessageCenterService(jdbc,principal,permissions);ReflectionTestUtils.setField(service,"queryCache",cache);
        assertThat(service.summary().unread()).isEqualTo(1);
        when(permissions.roleCodes(1L)).thenReturn(List.of());
        var hot=service.summary();assertThat(hot.unread()).isEqualTo(1);assertThat(hot.canSend()).isFalse();
        long summaryQueries=mockingDetails(jdbc).getInvocations().stream().filter(i->i.getMethod().getName().equals("query") && i.getMethod().getParameterTypes().length==3 && i.getMethod().getParameterTypes()[1]==RowCallbackHandler.class && i.getArguments()[0] instanceof String query && query.contains("select m.category")).count();
        assertThat(summaryQueries).isEqualTo(1);verify(principal,times(2)).require();verify(permissions,times(2)).roleCodes(1L);
        when(principal.require()).thenReturn(new AuthenticatedUser(2L,"other","session2",LocalDateTime.now().plusDays(1)));
        when(permissions.roleCodes(2L)).thenReturn(List.of());assertThat(service.summary().unread()).isZero();
        when(principal.require()).thenThrow(new IllegalStateException("revoked"));
        assertThatThrownBy(service::summary).hasMessage("revoked");
    }
    @Test void taskStatisticsAreReusedButTeamAccessAndFiltersAreStillChecked() throws Exception {
        var jdbc=mock(JdbcTemplate.class);var sql=mock(NamedParameterJdbcTemplate.class);
        var tenants=mock(TenantContextResolver.class);var permissions=mock(RbacPermissionService.class);
        var owner=new TenantContext(1L,2L,3L,"OWNER");when(tenants.requireActiveMember(2L)).thenReturn(owner);
        when(permissions.permissionCodes(any())).thenReturn(Set.of());
        doAnswer(inv->{var rs=mock(java.sql.ResultSet.class);when(rs.getString(1)).thenReturn("RUNNING");when(rs.getLong(2)).thenReturn(5L);inv.getArgument(2,RowCallbackHandler.class).processRow(rs);return null;}).when(sql).query(anyString(),anyMap(),any(RowCallbackHandler.class));
        var service=new ProductionTaskService(jdbc,tenants,permissions,mapper);
        ReflectionTestUtils.setField(service,"sql",sql);ReflectionTestUtils.setField(service,"queryCache",cache);
        var q=new ProductionTaskQuery("team",null,null,null,null,null,null,null,null);
        assertThat(service.summary(2L,q).total()).isEqualTo(5);assertThat(service.summary(2L,q).total()).isEqualTo(5);
        verify(sql,times(1)).query(anyString(),anyMap(),any(RowCallbackHandler.class));verify(tenants,times(2)).requireActiveMember(2L);
        when(tenants.requireActiveMember(2L)).thenReturn(new TenantContext(1L,2L,3L,"MEMBER"));
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(),any())).thenReturn(0L);
        assertThatThrownBy(()->service.summary(2L,q)).isInstanceOf(com.antshorttv.common.BusinessException.class);
        verify(sql,times(1)).query(anyString(),anyMap(),any(RowCallbackHandler.class));
    }
    @Test void publicModelOptionsAvoidRepeatedRoutingButStillCheckProjectAndMembership() {
        var tenants=mock(TenantContextResolver.class);var projects=mock(ProjectMapper.class);
        var models=mock(AiModelMapper.class);var router=mock(AiModelRouter.class);
        when(tenants.requireActiveMember(2L)).thenReturn(new TenantContext(1L,2L,3L,"OWNER"));
        var project=new ProjectEntity();project.id=4L;project.tenantId=2L;when(projects.selectByTenantIdAndId(2L,4L)).thenReturn(project);
        when(models.selectList(any())).thenReturn(List.of());
        var service=new ProjectAiConfigService(tenants,projects,mock(ProjectAiConfigMapper.class),models,router,mock(OperationLogService.class),mapper);
        ReflectionTestUtils.setField(service,"queryCache",cache);
        Object cold=service.availableModels(2L,4L);Object hot=service.availableModels(2L,4L);assertThat(hot).isEqualTo(cold);
        verify(models,times(4)).selectList(any());verify(projects,times(2)).selectByTenantIdAndId(2L,4L);
        when(tenants.requireActiveMember(2L)).thenThrow(new IllegalStateException("membership removed"));
        assertThatThrownBy(()->service.availableModels(2L,4L)).hasMessage("membership removed");
    }
}
