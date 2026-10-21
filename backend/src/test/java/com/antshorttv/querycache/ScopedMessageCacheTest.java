package com.antshorttv.querycache;

import com.antshorttv.authsession.AuthenticatedUser;
import com.antshorttv.messagecenter.MessageCenterService;
import com.antshorttv.platform.PlatformAuthorizationService;
import com.antshorttv.security.CurrentPrincipal;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScopedMessageCacheTest {
    AnnotationConfigApplicationContext context;
    MessageCenterService messages;
    CurrentPrincipal principal;
    QueryResultCacheTest.Store store;
    MeterRegistry metrics;
    JdbcTemplate jdbc;
    @BeforeEach void setup() {
        context=new AnnotationConfigApplicationContext(QueryCacheTransactionTest.Config.class);
        jdbc=context.getBean(JdbcTemplate.class);
        jdbc.execute("create table inbox_message(id varchar(36),category varchar(32),title varchar(200),content clob,source varchar(32),sender_id bigint,sender_name varchar(100),created_at timestamp,audience varchar(32))");
        jdbc.execute("create table message_recipient(message_id varchar(36),user_id bigint,deleted_at timestamp,read_at timestamp)");
        jdbc.execute("create table app_user(id bigint,nickname varchar(100),status varchar(20),deleted_at timestamp)");
        jdbc.update("insert into app_user values (1,'one','ACTIVE',null),(2,'two','ACTIVE',null),(3,'admin','ACTIVE',null)");
        messages=context.getBean(MessageCenterService.class);
        var target=AopTestUtils.<MessageCenterService>getTargetObject(messages);
        principal=(CurrentPrincipal)ReflectionTestUtils.getField(target,"principal");
        var auth=(PlatformAuthorizationService)ReflectionTestUtils.getField(target,"authorization");
        when(auth.roleCodes(2L)).thenReturn(List.of());when(auth.roleCodes(3L)).thenReturn(List.of("PLATFORM_ADMIN"));
        store=context.getBean(QueryResultCacheTest.Store.class);
        metrics=(MeterRegistry)ReflectionTestUtils.getField(context.getBean(QueryResultCache.class),"metrics");
    }
    @AfterEach void close() { if(context!=null)context.close(); }
    void as(long user) { when(principal.require()).thenReturn(new AuthenticatedUser(user,"mobile","session",LocalDateTime.now().plusDays(1))); }
    long unread(long user) { as(user);return messages.summary().unread(); }
    double misses() { return metrics.counter("antv.query.cache","region","MESSAGES","outcome","miss").count(); }
    Map<String,String> generations() { return store.values.entrySet().stream().filter(e->e.getKey().endsWith(":generation")).collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue)); }
    @Test void targetedSendReadDeleteAndSystemSendDoNotDiscardOtherUsers() {
        assertThat(unread(1)).isZero();assertThat(unread(2)).isZero();
        as(3);var sent=messages.send(new MessageCenterService.SendRequest("PRODUCT","title","body",false,List.of(1L)));
        assertThat(unread(2)).isZero();assertThat(misses()).isEqualTo(2);
        assertThat(unread(1)).isEqualTo(1);assertThat(misses()).isEqualTo(3);
        as(1);messages.update(false,new MessageCenterService.ActionRequest(List.of(sent.id()),false,null,null,null));
        assertThat(unread(2)).isZero();assertThat(misses()).isEqualTo(3);
        assertThat(unread(1)).isZero();assertThat(misses()).isEqualTo(4);
        messages.sendSystem(List.of(1L),"SYSTEM","system","body");
        assertThat(unread(2)).isZero();assertThat(misses()).isEqualTo(4);
        assertThat(unread(1)).isEqualTo(1);
        messages.update(true,new MessageCenterService.ActionRequest(null,true,null,null,null));
        assertThat(unread(1)).isZero();double after=misses();
        assertThat(unread(2)).isZero();assertThat(misses()).isEqualTo(after);
    }
    @Test void broadcastInvalidatesEveryUserAndRolledBackSendPublishesNoGeneration() {
        unread(1);unread(2);as(3);
        var before=generations();
        new TransactionTemplate(context.getBean(DataSourceTransactionManager.class)).executeWithoutResult(status->{
            messages.send(new MessageCenterService.SendRequest("PRODUCT","rollback","body",false,List.of(1L)));
            assertThat(generations()).isEqualTo(before);status.setRollbackOnly();
        });
        assertThat(generations()).isEqualTo(before);
        assertThat(unread(1)).isZero();assertThat(unread(2)).isZero();assertThat(misses()).isEqualTo(2);
        as(3);messages.send(new MessageCenterService.SendRequest("PRODUCT","broadcast","body",true,null));
        assertThat(unread(1)).isEqualTo(1);assertThat(unread(2)).isEqualTo(1);assertThat(misses()).isEqualTo(4);
    }
}
