package com.antshorttv.script;

import com.antshorttv.ai.AiSecretCodec;
import com.antshorttv.project.ProjectAccessContext;
import com.antshorttv.project.ProjectAccessResolver;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.querycache.CacheStore;
import com.antshorttv.querycache.QueryResultCache;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScriptQueryCacheTest {
    @Test void navigationAndVersionsRoundTripAndProjectAccessIsAlwaysChecked() throws Exception {
        Map<Class<?>,Object> dependencies=new HashMap<>();
        var constructor=ScriptWorkflowService.class.getConstructors()[0];
        Object[] arguments=Arrays.stream(constructor.getParameterTypes())
            .map(type->dependencies.computeIfAbsent(type,org.mockito.Mockito::mock)).toArray();
        var service=(ScriptWorkflowService)constructor.newInstance(arguments);
        var tenant=(TenantContextResolver)dependencies.get(TenantContextResolver.class);
        when(tenant.requireActiveMember(10L)).thenReturn(new TenantContext(1L,10L,1L,"OWNER"));
        var access=mock(ProjectAccessContext.class);
        var project=new ProjectEntity();project.id=33L;
        when(access.project()).thenReturn(project);
        var resolver=(ProjectAccessResolver)dependencies.get(ProjectAccessResolver.class);
        when(resolver.requireView(10L,33L)).thenReturn(access);
        var script=new ScriptEntity();script.setId(7L);script.setTenantId(10L);script.setProjectId(33L);script.setContent("example");
        var scripts=(ScriptMapper)dependencies.get(ScriptMapper.class);
        when(scripts.selectCurrentByProject(10L,33L)).thenReturn(script);
        var versions=(ScriptVersionMapper)dependencies.get(ScriptVersionMapper.class);
        var version=new ScriptVersionEntity();version.setId(8L);version.setVersionNo(1);version.setCreatedAt(LocalDateTime.of(2026,10,21,10,0));
        when(versions.selectByScript(10L,7L)).thenReturn(List.of(version));
        var episodes=mock(ScriptEpisodeService.class);
        when(episodes.currentEpisodeNavigation(10L,33L,7L)).thenReturn(List.of(
            new ScriptEpisodeNavigation(9L,1,"episode","summary","hash",null,null)));
        ReflectionTestUtils.setField(service,"scriptEpisodeService",episodes);
        var values=new HashMap<String,String>();
        CacheStore store=new CacheStore() {
            public String get(String key) { return values.get(key); }
            public void put(String key,String value,Duration ttl) { values.put(key,value); }
        };
        var mapper=new ObjectMapper().findAndRegisterModules();
        ReflectionTestUtils.setField(service,"queryCache",new QueryResultCache(store,mapper,
            new AiSecretCodec("script-test"),new SimpleMeterRegistry(),true));
        var cold=service.scriptPageWorkspace(10L,33L);
        var hot=service.scriptPageWorkspace(10L,33L);
        assertThat(mapper.writeValueAsString(hot)).isEqualTo(mapper.writeValueAsString(cold));
        verify(versions,times(1)).selectByScript(10L,7L);
        verify(episodes,times(1)).currentEpisodeNavigation(10L,33L,7L);
        verify(resolver,times(2)).requireView(10L,33L);
        clearInvocations(scripts);
        assertThat(service.currentAnalysis(10L,33L)).isNull();
        assertThat(service.currentAnalysis(10L,33L)).isNull();
        verify(scripts,times(1)).selectCurrentByProject(10L,33L);
        version.setTenantId(10L);version.setProjectId(33L);version.setScriptId(7L);version.setContent("updated");
        when(versions.selectById(8L)).thenReturn(version);when(scripts.selectById(7L)).thenReturn(script);
        service.applyVersion(10L,33L,8L,null);
        service.scriptPageWorkspace(10L,33L);
        verify(versions,times(2)).selectByScript(10L,7L);
        verify(episodes,times(2)).currentEpisodeNavigation(10L,33L,7L);
        when(resolver.requireView(10L,33L)).thenThrow(new IllegalStateException("project access revoked"));
        assertThatThrownBy(()->service.scriptPageWorkspace(10L,33L)).hasMessage("project access revoked");
        assertThatThrownBy(()->service.currentAnalysis(10L,33L)).hasMessage("project access revoked");
    }
}
