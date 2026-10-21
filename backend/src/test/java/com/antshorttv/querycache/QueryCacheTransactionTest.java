package com.antshorttv.querycache;

import com.antshorttv.ai.AiSecretCodec;
import com.antshorttv.messagecenter.MessageCenterService;
import com.antshorttv.platform.PlatformAuthorizationService;
import com.antshorttv.authsession.AuthenticatedUser;
import com.antshorttv.security.CurrentPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryCacheTransactionTest {
    @Configuration
    @EnableAspectJAutoProxy
    @EnableTransactionManagement
    static class Config {
        @Bean DriverManagerDataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:cache_tx_"+java.util.UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        }
        @Bean JdbcTemplate jdbc(DriverManagerDataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean DataSourceTransactionManager transactionManager(DriverManagerDataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
        @Bean QueryResultCacheTest.Store store() { return new QueryResultCacheTest.Store(); }
        @Bean QueryResultCache cache(QueryResultCacheTest.Store store) {
            return new QueryResultCache(store,new ObjectMapper(),new AiSecretCodec("test-key"),new SimpleMeterRegistry(),true);
        }
        @Bean QueryCacheInvalidationAspect aspect(QueryResultCache cache) { return new QueryCacheInvalidationAspect(cache); }
        @Bean MessageCenterService messages(JdbcTemplate jdbc) {
            var principal=mock(CurrentPrincipal.class);
            when(principal.require()).thenReturn(new AuthenticatedUser(1L,"mobile","session",LocalDateTime.now().plusDays(1)));
            var permissions=mock(PlatformAuthorizationService.class);
            when(permissions.roleCodes(1L)).thenReturn(List.of());
            return new MessageCenterService(jdbc,principal,permissions);
        }
    }
    @Test void realSpringProxyInvalidatesAfterCommitButNotAfterRollback() {
        try(var context=new AnnotationConfigApplicationContext(Config.class)) {
            var jdbc=context.getBean(JdbcTemplate.class);
            jdbc.execute("create table inbox_message(id varchar(36),category varchar(32))");
            jdbc.execute("create table message_recipient(message_id varchar(36),user_id bigint,deleted_at timestamp,read_at timestamp)");
            jdbc.update("insert into inbox_message values ('m','PRODUCT')");
            jdbc.update("insert into message_recipient values ('m',1,null,null)");
            var messages=context.getBean(MessageCenterService.class);
            var store=context.getBean(QueryResultCacheTest.Store.class);
            var transaction=new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
            var markAllRead=new MessageCenterService.ActionRequest(null,true,null,null,null);
            assertThat(messages.summary().unread()).isEqualTo(1);
            transaction.executeWithoutResult(status->{
                messages.update(false,markAllRead);
                assertThat(store.values.keySet()).noneMatch(k->k.endsWith(":generation"));
                status.setRollbackOnly();
            });
            assertThat(store.values.keySet()).noneMatch(k->k.endsWith(":generation"));
            assertThat(jdbc.queryForObject("select count(*) from message_recipient where read_at is null",Long.class)).isEqualTo(1L);
            assertThat(messages.summary().unread()).isEqualTo(1);
            messages.update(false,markAllRead);
            assertThat(store.values.keySet()).anyMatch(k->k.endsWith(":generation"));
            assertThat(messages.summary().unread()).isZero();
        }
    }
}
