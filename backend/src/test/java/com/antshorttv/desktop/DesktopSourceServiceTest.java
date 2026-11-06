package com.antshorttv.desktop;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.antshorttv.authsession.AuthenticatedUser;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.member.*;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.*;
import com.antshorttv.tenant.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class DesktopSourceServiceTest {
    JdbcTemplate jdbc;
    DriverManagerDataSource dataSource;
    DesktopSourceService service;
    TenantMemberMapper members;
    ProjectPermissionGuard guard;
    TenantMemberEntity member;

    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=30000", "sa", "");
        dataSource=ds;
        jdbc = spy(new JdbcTemplate(ds));
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V176__desktop_source_assets.sql")).execute(ds);
        jdbc.execute("create table project(id bigint primary key, tenant_id bigint, name varchar(200), deleted_at datetime)");
        for (String table : List.of("character_asset", "scene_asset", "prop_asset"))
            jdbc.execute("create table " + table + "(id bigint primary key,tenant_id bigint,project_id bigint,name varchar(100),prompt text,deleted_at datetime)");
        jdbc.execute("create table asset_visual_variant(id bigint primary key,tenant_id bigint,project_id bigint,asset_type varchar(32),asset_id bigint,name varchar(100),prompt text,is_primary boolean,deleted_at datetime)");
        jdbc.update("insert into project values(10,1,'Target',null),(11,1,'Other',null),(20,2,'Secret',null)");
        var tenants = mock(TenantMapper.class);
        members = mock(TenantMemberMapper.class);
        var principal = mock(CurrentPrincipal.class);
        when(principal.require()).thenReturn(new AuthenticatedUser(1L,"mobile","session",LocalDateTime.now().plusHours(1)));
        for (long id : List.of(1L,2L)) {
            var tenant = new TenantEntity(); tenant.setId(id); tenant.setStatus("ACTIVE");
            when(tenants.selectById(id)).thenReturn(tenant);
        }
        member = new TenantMemberEntity(); member.setId(1L); member.setStatus("ACTIVE"); member.setMemberType("OWNER");
        when(members.selectByTenantIdAndUserId(1L,1L)).thenReturn(member);
        guard = mock(ProjectPermissionGuard.class);
        service = new DesktopSourceService(jdbc,new TenantContextResolver(tenants,members,principal),guard);
    }

    @Test void syncIsIdempotentPartialSafeAndExplicitCompleteRetiresOmissions() {
        var p = sync(true, a("a","Hero"),a("b","Room"));
        assertThat(sync(false,a("a","Hero")).id()).isEqualTo(p.id());
        assertThat(service.assets(1L,p.id())).hasSize(2).allMatch(DesktopSourceModels.SourceAsset::active);
        sync(true,a("a","Hero"));
        assertThat(service.assets(1L,p.id())).filteredOn(x -> !x.active()).extracting(DesktopSourceModels.SourceAsset::sourceAssetId).containsExactly("b");
        assertThat(jdbc.queryForObject("select count(*) from desktop_source_project",Integer.class)).isEqualTo(1);
    }

    @Test void currentSyncRetiresOnlyItsOwnProjectHistoryAndCarriesUniqueBinding() {
        asset("character_asset",1,"Hero","prompt");
        var old=sync(true,a("old","Hero"));
        service.bind(1L,old.id(),10L);
        Long oldAsset=service.match(1L,old.id()).get(0).id();
        for(var identity:List.of(
            new String[]{"other","team","project"},new String[]{"local-session","other","project"},
            new String[]{"local-session","team","other"}))
            service.sync(1L,new DesktopSourceModels.SyncRequest(identity[0],identity[1],identity[2],"script","Other",true,List.of()));
        var current=service.syncCurrent(1L,currentRequest("current",true,a("new","Hero")));
        assertThat(current.targetProjectId()).isEqualTo(10L);
        assertThat(service.list(1L)).hasSize(4).extracting(DesktopSourceModels.SourceProject::id).doesNotContain(old.id());
        assertThat(jdbc.queryForObject("select active from desktop_source_asset where id=?",Boolean.class,oldAsset)).isFalse();
        assertThatThrownBy(()->service.assets(1L,old.id())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.bind(1L,old.id(),10L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.manualMatch(1L,oldAsset,1L,null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.prompts(1L,List.of(oldAsset))).isInstanceOf(BusinessException.class);
        assertThat(service.assets(1L,current.id())).extracting(DesktopSourceModels.SourceAsset::matchStatus).containsExactly("UNMATCHED");
        assertThat(service.syncCurrent(1L,currentRequest("current",true,a("new","Hero"))).id()).isEqualTo(current.id());
    }

    @Test void currentSyncPreservesCurrentMappingsAndDoesNotGuessConflictingBindings() {
        asset("character_asset",1,"Hero","prompt");
        var first=sync(true,a("a","Hero")); service.bind(1L,first.id(),10L);
        var second=service.sync(1L,currentRequest("second",true,a("b","Hero"))); service.bind(1L,second.id(),11L);
        var current=service.syncCurrent(1L,currentRequest("current",true,a("c","Hero")));
        assertThat(current.targetProjectId()).isNull();
        service.bind(1L,current.id(),10L);
        Long matched=service.match(1L,current.id()).get(0).targetAssetId();
        service.syncCurrent(1L,currentRequest("current",true,a("c","Hero")));
        assertThat(service.assets(1L,current.id()).get(0).targetAssetId()).isEqualTo(matched);
        assertThat(service.list(1L)).extracting(DesktopSourceModels.SourceProject::id).containsExactly(current.id());
    }

    @Test void currentSyncRequiresCompleteAndRollsBackInvalidSnapshots() {
        var old=sync(true,a("a","Hero"));
        var proxy=new org.springframework.aop.framework.ProxyFactory(service);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource),
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var transactional=(DesktopSourceService)proxy.getProxy();
        assertThatThrownBy(()->transactional.syncCurrent(1L,currentRequest("new",false,a("n","New")))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->transactional.syncCurrent(1L,currentRequest("new",true,a("n","New","CHARACTER","missing")))).isInstanceOf(BusinessException.class);
        assertThat(service.list(1L)).extracting(DesktopSourceModels.SourceProject::id).containsExactly(old.id());
        assertThat(service.assets(1L,old.id()).get(0).active()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from desktop_source_project",Integer.class)).isEqualTo(1);
    }

    private DesktopSourceModels.SyncRequest currentRequest(String script,boolean complete,DesktopSourceModels.SyncAsset... assets) {
        return new DesktopSourceModels.SyncRequest("local-session","team","project",script,"Source",complete,List.of(assets));
    }

    @Test void concurrentCurrentReplacementsLeaveExactlyOneActiveInventory() throws Exception {
        sync(true,a("old","Hero"));
        var proxy=new org.springframework.aop.framework.ProxyFactory(service);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource),
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var transactional=(DesktopSourceService)proxy.getProxy();
        var start=new java.util.concurrent.CyclicBarrier(2);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var futures=new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for(String script:List.of("first","second")) futures.add(executor.submit(()->{
                start.await(10,java.util.concurrent.TimeUnit.SECONDS);
                try { transactional.syncCurrent(1L,currentRequest(script,true,a(script,"Hero"))); return true; }
                catch(BusinessException e) {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    return false;
                }
            }));
            assertThat(List.of(futures.get(0).get(40,java.util.concurrent.TimeUnit.SECONDS),futures.get(1).get(40,java.util.concurrent.TimeUnit.SECONDS)))
                .contains(true);
            assertThat(service.list(1L)).hasSize(1);
            var current=service.list(1L).get(0);
            assertThat(current.sourceScriptId()).isIn("first","second");
            assertThat(jdbc.queryForObject("select count(*) from desktop_source_asset where active=true",Integer.class)).isEqualTo(1);
            assertThat(service.assets(1L,current.id())).filteredOn(DesktopSourceModels.SourceAsset::active)
                .extracting(DesktopSourceModels.SourceAsset::sourceAssetId).containsExactly(current.sourceScriptId());
        } finally { executor.shutdownNow(); }
    }

    @Test void matchesOnlyExactTypeAndNameAndReportsAmbiguityOrMissingPrompt() {
        asset("character_asset",1,"Hero","base"); asset("character_asset",2,"Twin","one"); asset("character_asset",3,"Twin","two");
        asset("scene_asset",4,"Room",""); asset("prop_asset",5,"Hero","wrong type");
        var p=sync(true,a("a"," Hero "),a("b","Twin"),a("c","Room","SCENE",null),a("d","hero"));
        service.bind(1L,p.id(),10L);
        assertThat(service.match(1L,p.id())).extracting(DesktopSourceModels.SourceAsset::matchStatus)
            .containsExactly("MATCHED","CONFLICT","NO_PROMPT","UNMATCHED");
        verify(guard,atLeastOnce()).require(1L,10L,"ELEMENT:VIEW");
    }

    @Test void preservesOriginalSourceNamesAndOnlyTrimsForMatching() {
        asset("character_asset",1,"Hero","base");
        var p=service.sync(1L,new DesktopSourceModels.SyncRequest("local-session","team","project","script"," Source ",true,
            List.of(a("a"," Hero "))));
        assertThat(p.name()).isEqualTo(" Source ");
        assertThat(service.assets(1L,p.id()).get(0).name()).isEqualTo(" Hero ");
        service.bind(1L,p.id(),10L);
        var matched=service.match(1L,p.id()).get(0);
        assertThat(matched.matchStatus()).isEqualTo("MATCHED");
        assertThat(matched.name()).isEqualTo(" Hero ");
    }

    @Test void syncPreservesLiteralAssetLineBreaksAndTabsButKeepsProjectValidationUnchanged() {
        String literal="  Hero\tlook\r\nWinter ";
        var p=sync(true,a("raw",literal));
        assertThat(service.assets(1L,p.id()).get(0).name()).isEqualTo(literal);
        for(char control:new char[]{'\u0000','\u000b','\u007f','\u0085'})
            assertThatThrownBy(()->sync(false,a("invalid","Hero"+control))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->sync(false,a("blank","\r\n\t "))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.sync(1L,new DesktopSourceModels.SyncRequest("local-session","team","project","script",
            "Invalid\nproject",true,List.of(a("valid","Hero"))))).isInstanceOf(BusinessException.class);
        assertThat(service.assets(1L,p.id()).get(0).name()).isEqualTo(literal);
    }

    @Test void variantsAreScopedToMatchedParentAndUseTheirPersistedPromptSource() {
        asset("character_asset",1,"Hero","canonical base"); asset("character_asset",2,"Other","other base");
        variant(101,1,"Default","stale primary",true); variant(102,1,"Winter","wear coat",false); variant(103,2,"Winter","wrong",false);
        var p=sync(true,a("p","Hero"),a("v","Winter","CHARACTER","p"),a("main","Default","CHARACTER","p"));
        service.bind(1L,p.id(),10L);
        var matched=service.match(1L,p.id());
        assertThat(matched.get(1).targetVariantId()).isEqualTo(102L);
        assertThat(service.prompts(1L,List.of(matched.get(1).id(),matched.get(2).id())))
            .extracting(DesktopSourceModels.Prompt::prompt).containsExactly("wear coat","canonical base");
        assertThat(service.candidates(1L,p.id(),"CHARACTER","p")).extracting(DesktopSourceModels.Candidate::variantId).containsExactly(101L,102L);
        assertThatThrownBy(() -> service.manualMatch(1L,matched.get(1).id(),2L,103L)).isInstanceOf(BusinessException.class);
    }

    @Test void renamesRequireReviewAndDeletedOrRenamedTargetsCannotReturnPrompts() {
        asset("character_asset",1,"Hero","base");
        var p=sync(true,a("a","Hero")); service.bind(1L,p.id(),10L);
        var row=service.match(1L,p.id()).get(0);
        sync(true,a("a","New Hero"));
        assertThat(service.match(1L,p.id()).get(0).matchStatus()).isEqualTo("REVIEW");
        service.manualMatch(1L,row.id(),1L,null);
        jdbc.update("update character_asset set name='Renamed' where id=1");
        assertThat(service.assets(1L,p.id()).get(0).matchStatus()).isEqualTo("REVIEW");
        assertThatThrownBy(() -> service.prompts(1L,List.of(row.id()))).isInstanceOf(BusinessException.class);
        jdbc.update("update character_asset set deleted_at=now() where id=1");
        assertThatThrownBy(() -> service.prompts(1L,List.of(row.id()))).isInstanceOf(BusinessException.class);
    }

    @Test void rebindingClearsMappingsAndRejectsCrossProjectOrTypeManualSelections() {
        asset("character_asset",1,"Hero","base"); asset("prop_asset",2,"Hero","prop");
        var p=sync(true,a("a","Hero")); service.bind(1L,p.id(),10L);
        var row=service.match(1L,p.id()).get(0);
        assertThatThrownBy(() -> service.manualMatch(1L,row.id(),2L,null)).isInstanceOf(BusinessException.class);
        service.bind(1L,p.id(),11L);
        assertThat(service.assets(1L,p.id()).get(0).targetAssetId()).isNull();
        assertThatThrownBy(() -> service.manualMatch(1L,row.id(),1L,null)).isInstanceOf(BusinessException.class);
    }

    @Test void tenantMembershipIsRequiredOnEveryOperationAndRowsAreIsolated() {
        var p=sync(true,a("a","Hero")); var row=service.assets(1L,p.id()).get(0);
        assertThatThrownBy(() -> service.assets(2L,p.id())).isInstanceOf(BusinessException.class);
        member.setStatus("REMOVED");
        for (Runnable call : List.<Runnable>of(() -> service.list(1L),() -> sync(false,a("a","Hero")),
            () -> service.assets(1L,p.id()),() -> service.bind(1L,p.id(),10L),() -> service.match(1L,p.id()),
            () -> service.candidates(1L,p.id(),"CHARACTER",null),() -> service.manualMatch(1L,row.id(),1L,null),
            () -> service.prompts(1L,List.of(row.id())))) {
            assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException)ex).getErrorCode()).isEqualTo(ErrorCode.MEMBER_REMOVED));
        }
    }

    @Test void targetPermissionRevocationBlocksAllBoundReadsAndWrites() {
        var p=sync(true,a("a","Hero")); service.bind(1L,p.id(),10L);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN,"denied")).when(guard).require(1L,10L,"ELEMENT:VIEW");
        assertThat(service.list(1L)).isEmpty();
        assertThatThrownBy(() -> service.assets(1L,p.id())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.match(1L,p.id())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.candidates(1L,p.id(),"CHARACTER",null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> sync(false,a("a","Hero"))).isInstanceOf(BusinessException.class);
    }

    @Test void manualResolutionSurvivesAutomaticRematchAndEmptyOrInactivePromptsAreBlocked() {
        asset("character_asset",1,"Twin","first"); asset("character_asset",2,"Twin","chosen");
        var p=sync(true,a("a","Twin")); service.bind(1L,p.id(),10L);
        var row=service.match(1L,p.id()).get(0);
        assertThat(row.matchStatus()).isEqualTo("CONFLICT");
        service.manualMatch(1L,row.id(),2L,null);
        assertThat(service.match(1L,p.id()).get(0).targetAssetId()).isEqualTo(2L);
        assertThat(service.prompts(1L,List.of(row.id())).get(0).prompt()).isEqualTo("chosen");
        jdbc.update("update character_asset set prompt='  ' where id=2");
        assertThat(service.assets(1L,p.id()).get(0).matchStatus()).isEqualTo("NO_PROMPT");
        assertThatThrownBy(()->service.prompts(1L,List.of(row.id()))).isInstanceOf(BusinessException.class);
        jdbc.update("update character_asset set prompt='restored' where id=2");
        sync(true);
        assertThat(service.assets(1L,p.id()).get(0).matchStatus()).isEqualTo("INACTIVE");
        assertThatThrownBy(()->service.prompts(1L,List.of(row.id()))).isInstanceOf(BusinessException.class);
    }

    @Test void currentTenantMembersCannotReadOtherTenantsInventoryOrChooseForeignTargets() {
        var p=sync(true,a("a","Hero"));
        when(members.selectByTenantIdAndUserId(2L,1L)).thenReturn(member);
        assertThat(service.list(2L)).isEmpty();
        assertThatThrownBy(()->service.assets(2L,p.id())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.bind(1L,p.id(),20L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.prompts(1L,List.of(9999L))).isInstanceOf(BusinessException.class);
    }

    @Test void readsTargetCatalogOncePerScopeAndRefreshesPromptsOnTheNextOperation() {
        asset("character_asset",1,"Hero","base"); variant(101,1,"Winter","coat",false);
        var many=new java.util.ArrayList<DesktopSourceModels.SyncAsset>();
        for(int i=0;i<30;i++) {
            many.add(a("main"+i,"Hero"));
            many.add(a("variant"+i,"Winter","CHARACTER","main"+i));
        }
        var p=sync(true,many.toArray(DesktopSourceModels.SyncAsset[]::new)); service.bind(1L,p.id(),10L);
        clearInvocations(jdbc);
        var matched=service.match(1L,p.id());
        assertThat(matched).allMatch(row->row.matchStatus().equals("MATCHED"));
        assertThat(targetCatalogReads()).as("matching reads each subject/variant catalog once, shared by all source parents").isEqualTo(2);
        clearInvocations(jdbc);
        assertThat(service.assets(1L,p.id())).hasSize(60);
        assertThat(targetCatalogReads()).as("listing mapped inventory does not reload targets for each source row").isEqualTo(2);
        jdbc.update("update character_asset set prompt='new base' where id=1");
        clearInvocations(jdbc);
        assertThat(service.prompts(1L,matched.stream().map(DesktopSourceModels.SourceAsset::id).toList()))
            .extracting(DesktopSourceModels.Prompt::prompt).containsOnly("new base","coat");
        assertThat(targetCatalogReads()).as("prompt reads are bounded by target scopes and reloaded for the new operation").isEqualTo(2);
    }

    private long targetCatalogReads() {
        return mockingDetails(jdbc).getInvocations().stream().filter(invocation->invocation.getMethod().getName().equals("query"))
            .filter(invocation->invocation.getArguments().length>1 && invocation.getArguments()[1] instanceof org.springframework.jdbc.core.RowMapper)
            .map(invocation->invocation.getArguments()[0]).filter(String.class::isInstance).map(String.class::cast)
            .filter(sql->sql.contains("select id,name,prompt from character_asset") || sql.contains("from asset_visual_variant v join character_asset")).count();
    }

    @Test void manualSelectionUsesSourceStateCommittedBeforeTheProjectLockWasAcquired() {
        asset("character_asset",1,"Hero","base");
        var p=sync(true,a("a","Hero")); service.bind(1L,p.id(),10L);
        var row=service.match(1L,p.id()).get(0);
        doAnswer(invocation->{
            jdbc.update("update desktop_source_asset set asset_type='SCENE',name='Room',match_status='REVIEW' where id=?",row.id());
            return invocation.callRealMethod();
        }).when(jdbc).query(eq("select * from desktop_source_project where id=? and tenant_id=? for update"),
            org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any(),any(Object[].class));
        assertThatThrownBy(()->service.manualMatch(1L,row.id(),1L,null)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select match_status from desktop_source_asset where id=?",String.class,row.id())).isEqualTo("REVIEW");
    }

    @Test void bindingResponseUsesCurrentLockedBindingEvenWhenThereIsNoWrite() {
        var p=sync(true,a("a","Hero")); service.bind(1L,p.id(),11L);
        doAnswer(invocation->{
            // Model a repeatable-read snapshot from before another transaction committed target 11.
            jdbc.update("update desktop_source_project set target_project_id=10 where id=?",p.id());
            try { return invocation.callRealMethod(); }
            finally { jdbc.update("update desktop_source_project set target_project_id=11 where id=?",p.id()); }
        }).when(jdbc).query(eq("select * from desktop_source_project where id=? and tenant_id=?"),
            org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any(),any(Object[].class));
        assertThat(service.bind(1L,p.id(),11L).targetProjectId()).isEqualTo(11L);
    }

    @Test void promptsIncludeOriginalSourceProjectAndParentNamesAlongsideTargetOrigin() {
        asset("character_asset",1,"Hero","base"); variant(101,1,"Winter","coat",false);
        var p=sync(true,a("main"," Hero "),a("variant","Winter","CHARACTER","main"));
        service.bind(1L,p.id(),10L);
        var rows=service.match(1L,p.id());
        var values=service.prompts(1L,rows.stream().map(DesktopSourceModels.SourceAsset::id).toList());
        var json=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(values);
        assertThat(json.get(1).path("sourceProjectId").asLong()).isEqualTo(p.id());
        assertThat(json.get(1).path("sourceProjectName").asText()).isEqualTo("Source");
        assertThat(json.get(1).path("sourceParentName").asText()).isEqualTo(" Hero ");
        assertThat(json.get(0).path("sourceParentName").isNull()).isTrue();
        assertThat(json.get(1).path("projectName").asText()).isEqualTo("Target");
        assertThat(json.get(1).path("targetAssetId").asLong()).isEqualTo(1L);
        assertThat(json.get(1).path("targetVariantId").asLong()).isEqualTo(101L);
    }

    @Test void concurrentFirstPartialSyncsValidateTheMergedInventoryUnderTheIdentityLock() throws Exception {
        var proxy=new org.springframework.aop.framework.ProxyFactory(service);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource),
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var transactional=(DesktopSourceService)proxy.getProxy();
        var initialReads=new java.util.concurrent.CyclicBarrier(2);
        doAnswer(invocation->{
            Object result=invocation.callRealMethod();
            if(((List<?>)result).isEmpty()) initialReads.await(10,java.util.concurrent.TimeUnit.SECONDS);
            return result;
        }).when(jdbc).query(contains("source_script_id=? for update"),
            org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any(),any(Object[].class));
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var futures=new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for(String prefix:List.of("first","second")) futures.add(executor.submit(()->{
                var assets=new java.util.ArrayList<DesktopSourceModels.SyncAsset>();
                for(int i=0;i<2501;i++) assets.add(a(prefix+i,"Hero"));
                try {
                    transactional.sync(1L,new DesktopSourceModels.SyncRequest("local","team","project","script","Source",false,assets));
                    return true;
                } catch(BusinessException e) {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    return false;
                }
            }));
            assertThat(List.of(futures.get(0).get(60,java.util.concurrent.TimeUnit.SECONDS),futures.get(1).get(60,java.util.concurrent.TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("select count(*) from desktop_source_asset where active=true",Integer.class)).isEqualTo(2501);
        } finally { executor.shutdownNow(); }
    }

    @Test void rejectsDuplicatesCyclesUnknownParentsAndExcessiveInputs() {
        assertThatThrownBy(() -> sync(true,a("a","Hero"),a("a","Other"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> sync(true,a("a","Hero","CHARACTER","b"),a("b","Other","CHARACTER","a"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> sync(true,a("a","Hero","CHARACTER","unknown"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> sync(true,a("a","X".repeat(201)))).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select count(*) from desktop_source_asset",Integer.class)).isZero();
    }

    @Test void aiConfirmationRequiresAnUnchangedSnapshotAndComparesCandidatesWithoutOrdering() {
        asset("character_asset",1,"Hero A","base"); asset("character_asset",2,"Hero B","base"); asset("character_asset",3,"No prompt","");
        var p=sync(true,a("a","Source hero")); service.bind(1L,p.id(),10L);
        var source=service.assets(1L,p.id()).get(0);
        var candidates=new java.util.ArrayList<>(service.candidates(1L,p.id(),"CHARACTER",null).stream().filter(DesktopSourceModels.Candidate::hasPrompt).toList());
        java.util.Collections.reverse(candidates);
        var saved=service.aiMatch(1L,source.id(),aiRequest(source,10L,candidates,2L,null));
        assertThat(saved.targetAssetId()).isEqualTo(2L);
        assertThat(saved.matchStatus()).isEqualTo("MATCHED");
        assertThatThrownBy(()->service.aiMatch(1L,source.id(),aiRequest(source,10L,candidates,1L,null)))
            .isInstanceOf(BusinessException.class).hasMessageContaining("重新匹配");
        assertThatThrownBy(()->service.aiMatch(1L,source.id(),aiRequest(saved,10L,candidates,1L,null)))
            .isInstanceOf(BusinessException.class).hasMessageContaining("重新匹配");
        assertThat(service.assets(1L,p.id()).get(0).targetAssetId()).isEqualTo(2L);
    }

    @Test void aiConfirmationRejectsRenamedReparentedOrInactiveSourcesAndChangedBinding() {
        asset("character_asset",1,"Hero","base");
        var p=sync(true,a("a","Source hero")); service.bind(1L,p.id(),10L);
        var source=service.assets(1L,p.id()).get(0);
        var request=aiRequest(source,10L,service.candidates(1L,p.id(),"CHARACTER",null),1L,null);
        for(String change:List.of("name='Renamed'","source_parent_id='another'","active=false")) {
            jdbc.update("update desktop_source_asset set "+change+" where id=?",source.id());
            assertThatThrownBy(()->service.aiMatch(1L,source.id(),request)).isInstanceOf(BusinessException.class).hasMessageContaining("重新匹配");
            jdbc.update("update desktop_source_asset set name=?,source_parent_id=null,active=true where id=?",source.name(),source.id());
        }
        service.bind(1L,p.id(),11L);
        assertThatThrownBy(()->service.aiMatch(1L,source.id(),request)).isInstanceOf(BusinessException.class).hasMessageContaining("重新匹配");
        assertThat(service.assets(1L,p.id()).get(0).targetAssetId()).isNull();
    }

    @Test void aiConfirmationRejectsAnySelectableCandidateChangeOrMissingPrompt() {
        asset("character_asset",1,"Hero A","base"); asset("character_asset",2,"Hero B","base");
        var p=sync(true,a("a","Source hero")); service.bind(1L,p.id(),10L);
        var source=service.assets(1L,p.id()).get(0);
        var request=aiRequest(source,10L,service.candidates(1L,p.id(),"CHARACTER",null),1L,null);
        for(String change:List.of("name='Renamed'","prompt=''","deleted_at=now()")) {
            jdbc.update("update character_asset set "+change+" where id=2");
            assertThatThrownBy(()->service.aiMatch(1L,source.id(),request)).isInstanceOf(BusinessException.class).hasMessageContaining("重新匹配");
            jdbc.update("update character_asset set name='Hero B',prompt='base',deleted_at=null where id=2");
        }
        asset("character_asset",3,"New candidate","base");
        assertThatThrownBy(()->service.aiMatch(1L,source.id(),request)).isInstanceOf(BusinessException.class).hasMessageContaining("重新匹配");
        jdbc.update("update character_asset set prompt='' where id=1");
        var refreshed=service.candidates(1L,p.id(),"CHARACTER",null).stream().filter(DesktopSourceModels.Candidate::hasPrompt).toList();
        assertThatThrownBy(()->service.aiMatch(1L,source.id(),aiRequest(source,10L,refreshed,1L,null))).isInstanceOf(BusinessException.class);
        assertThat(service.assets(1L,p.id()).get(0).targetAssetId()).isNull();
    }

    @Test void aiConfirmationUsesTheSourceMatchCommittedBeforeAcquiringTheProjectLock() {
        asset("character_asset",1,"Hero A","base"); asset("character_asset",2,"Hero B","base");
        var p=sync(true,a("a","Source hero")); service.bind(1L,p.id(),10L);
        var source=service.assets(1L,p.id()).get(0);
        var request=aiRequest(source,10L,service.candidates(1L,p.id(),"CHARACTER",null),1L,null);
        doAnswer(invocation->{
            jdbc.update("update desktop_source_asset set target_asset_id=2,target_name='Hero B',match_status='MATCHED' where id=?",source.id());
            return invocation.callRealMethod();
        }).when(jdbc).query(eq("select * from desktop_source_project where id=? and tenant_id=? for update"),
            org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any(),any(Object[].class));
        assertThatThrownBy(()->service.aiMatch(1L,source.id(),request)).isInstanceOf(BusinessException.class).hasMessageContaining("重新匹配");
        assertThat(jdbc.queryForObject("select target_asset_id from desktop_source_asset where id=?",Long.class,source.id())).isEqualTo(2L);
    }

    @Test void aiVariantConfirmationRemainsScopedToTheCurrentMatchedParent() {
        asset("character_asset",1,"Hero","base"); asset("character_asset",2,"Other","base");
        variant(101,1,"Winter","coat",false); variant(102,2,"Winter","other coat",false);
        var p=sync(true,a("p","Hero"),a("v","Source winter","CHARACTER","p")); service.bind(1L,p.id(),10L);
        var sources=service.match(1L,p.id()); var source=sources.get(1);
        var request=aiRequest(source,10L,service.candidates(1L,p.id(),"CHARACTER","p"),1L,101L);
        service.manualMatch(1L,sources.get(0).id(),2L,null);
        assertThatThrownBy(()->service.aiMatch(1L,source.id(),request)).isInstanceOf(BusinessException.class).hasMessageContaining("重新匹配");
        assertThat(service.assets(1L,p.id()).get(1).targetAssetId()).isNull();
    }

    @Test void nonPrimaryVariantPromptIsIndependentOfMissingCanonicalPromptAndNeverFallsBack() {
        asset("character_asset",1,"Hero","  ");
        variant(101,1,"Default","ignored historical primary prompt",true);
        variant(102,1,"Winter","wear coat",false); variant(103,1,"Empty","",false);
        var p=sync(true,a("p","Hero"),a("v","Source winter","CHARACTER","p"),
            a("primary","Default","CHARACTER","p"),a("empty","Empty","CHARACTER","p"));
        service.bind(1L,p.id(),10L);
        var sources=service.match(1L,p.id());
        assertThat(sources).extracting(DesktopSourceModels.SourceAsset::matchStatus)
            .containsExactly("NO_PROMPT","UNMATCHED","NO_PROMPT","NO_PROMPT");
        var candidates=service.candidates(1L,p.id(),"CHARACTER","p");
        assertThat(candidates).extracting(DesktopSourceModels.Candidate::hasPrompt).containsExactly(false,true,false);
        var saved=service.aiMatch(1L,sources.get(1).id(),aiRequest(sources.get(1),10L,
            candidates.stream().filter(DesktopSourceModels.Candidate::hasPrompt).toList(),1L,102L));
        assertThat(saved.matchStatus()).isEqualTo("MATCHED");
        assertThat(service.prompts(1L,List.of(saved.id())).get(0).prompt()).isEqualTo("wear coat");
        assertThatThrownBy(()->service.prompts(1L,List.of(sources.get(2).id()))).isInstanceOf(BusinessException.class);
        jdbc.update("update character_asset set prompt='new canonical prompt' where id=1");
        assertThat(service.prompts(1L,List.of(saved.id())).get(0).prompt()).isEqualTo("wear coat");
        assertThat(service.assets(1L,p.id()).get(3).matchStatus()).isEqualTo("NO_PROMPT");
    }

    @Test void simultaneousAiConfirmationsCannotOverwriteTheFirstCommittedMatch() throws Exception {
        asset("character_asset",1,"Hero A","base"); asset("character_asset",2,"Hero B","base");
        var p=sync(true,a("a","Source hero")); service.bind(1L,p.id(),10L);
        var source=service.assets(1L,p.id()).get(0);
        var candidates=service.candidates(1L,p.id(),"CHARACTER",null);
        var proxy=new org.springframework.aop.framework.ProxyFactory(service);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource),
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var transactional=(DesktopSourceService)proxy.getProxy();
        var initialReads=new java.util.concurrent.CyclicBarrier(2);
        doAnswer(invocation->{
            Object result=invocation.callRealMethod();
            initialReads.await(10,java.util.concurrent.TimeUnit.SECONDS);
            return result;
        }).when(jdbc).query(eq("select * from desktop_source_asset where id=? and tenant_id=?"),
            org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any(),any(Object[].class));
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var futures=new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for(Long target:List.of(1L,2L)) futures.add(executor.submit(()->{
                try { transactional.aiMatch(1L,source.id(),aiRequest(source,10L,candidates,target,null)); return true; }
                catch(BusinessException e) { assertThat(e.getMessage()).contains("重新匹配"); return false; }
            }));
            assertThat(List.of(futures.get(0).get(30,java.util.concurrent.TimeUnit.SECONDS),futures.get(1).get(30,java.util.concurrent.TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("select target_asset_id from desktop_source_asset where id=?",Long.class,source.id())).isIn(1L,2L);
        } finally { executor.shutdownNow(); }
    }

    private DesktopSourceModels.AiMatchRequest aiRequest(DesktopSourceModels.SourceAsset source,Long project,
            List<DesktopSourceModels.Candidate> candidates,Long asset,Long variant) {
        return new DesktopSourceModels.AiMatchRequest(asset,variant,new DesktopSourceModels.AiMatchExpected(
            source.name(),source.sourceParentId(),source.targetAssetId(),source.targetVariantId(),source.matchStatus(),project,candidates));
    }

    private DesktopSourceModels.SourceProject sync(boolean complete,DesktopSourceModels.SyncAsset... assets) {
        return service.sync(1L,new DesktopSourceModels.SyncRequest("local-session","team","project","script","Source",complete,List.of(assets)));
    }
    private DesktopSourceModels.SyncAsset a(String id,String name) { return a(id,name,"CHARACTER",null); }
    private DesktopSourceModels.SyncAsset a(String id,String name,String type,String parent) { return new DesktopSourceModels.SyncAsset(id,parent,name,type); }
    private void asset(String table,long id,String name,String prompt) { jdbc.update("insert into "+table+" values(?,1,10,?,?,null)",id,name,prompt); }
    private void variant(long id,long asset,String name,String prompt,boolean primary) { jdbc.update("insert into asset_visual_variant values(?,1,10,'CHARACTER',?,?,?,?,null)",id,asset,name,prompt,primary); }
}
