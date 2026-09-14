package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AssetCatalogServiceTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final ObjectMapper json=new ObjectMapper();
    private final AssetCatalogService service=new AssetCatalogService(jdbc,json);
    private ToolExecutionContext context() {
        var c=new ToolExecutionContext(1L,2L,3L,4L,5L,6L,null,7L,Set.of("SCRIPT:VIEW"),null,new WorkflowToolRunState());
        c.runState().put("assetScope","PROP");
        return c;
    }
    private List<Map<String,Object>> props() {
        return IntStream.rangeClosed(1,215).mapToObj(i->Map.<String,Object>of(
            "id",(long)i,"name","道具"+i,"normalized_name","道具"+i,
            "content_json",i==215?"{\"aliases\":[\"双环徽章\"]}":"{}","prompt","已有提示词")).toList();
    }
    @Test void pagesAll215IdentitiesAndResolvesAnAliasOutsideTheInitialPage() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(props());
        var c=context();
        var args=json.createObjectNode().put("assetType","PROP").put("pageSize",50);
        Set<String> seen=new HashSet<>();
        var page=service.search(c,args);
        while(true) {
            assertThat(page.path("items").size()).isLessThanOrEqualTo(50);
            page.path("items").forEach(i->assertThat(seen.add(i.path("assetKey").asText())).isTrue());
            if(!page.path("hasMore").asBoolean())break;
            args.put("cursor",page.path("nextCursor").asText());page=service.search(c,args);
        }
        assertThat(seen).hasSize(215);
        var match=service.search(c,json.createObjectNode().put("assetType","PROP").put("query","双环徽章"));
        assertThat(match.path("items").get(0).path("assetKey").asText()).isEqualTo("p_215");
        assertThat(match.path("items").get(0).path("hasPrompt").asBoolean()).isTrue();
    }
    @Test void rejectsCursorsFromAnotherRunOrQueryAndOutOfScopeTypes() {
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(props());
        var c=context();
        var page=service.search(c,json.createObjectNode().put("assetType","PROP"));
        var args=json.createObjectNode().put("assetType","PROP").put("cursor",page.path("nextCursor").asText());
        assertThatThrownBy(()->service.search(context(),args)).hasMessageContaining("游标");
        assertThatThrownBy(()->service.search(c,args.put("query","另一名称"))).hasMessageContaining("游标");
        assertThatThrownBy(()->service.search(c,json.createObjectNode().put("assetType","CHARACTER")))
            .hasMessageContaining("未授权");
    }
    @Test void candidatesDoNotExpandTheWholeScript() {
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(props());
        var catalog=service.candidates(context(),"双环徽章放在桌上");
        assertThat(catalog.path("props").size()).isEqualTo(1);
        assertThat(catalog.path("props").get(0).path("assetKey").asText()).isEqualTo("p_215");
        assertThat(catalog.path("pages").path("props").path("hasMore").asBoolean()).isTrue();
        assertThat(catalog.path("characters").isEmpty()).isTrue();
    }
    @Test void oversizedSingleDetailAndTooManyKeysFailExplicitly() {
        var content=json.createObjectNode().put("description","长".repeat(40_000));
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of(
            "id",1L,"name","徽章","normalized_name","徽章","content_json",content.toString())));
        var args=json.createObjectNode();args.putArray("assetKeys").add("p_1");
        assertThatThrownBy(()->service.details(context(),args)).hasMessageContaining("单条资产详情");
        for(int i=2;i<=11;i++) ((com.fasterxml.jackson.databind.node.ArrayNode)args.path("assetKeys")).add("p_"+i);
        assertThatThrownBy(()->service.details(context(),args)).hasMessageContaining("1 到 10");
    }

    @Test void pages51VariantsWithoutAWholeCatalogFailure() {
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(invocation->{
            String sql=invocation.getArgument(0);
            if(sql.contains("from prop_asset")) return List.of(props().get(0));
            long after=((Number)invocation.getArguments()[5]).longValue();
            return IntStream.rangeClosed(1,51).filter(i->i>after).limit(21)
                .mapToObj(i->Map.<String,Object>of("id",(long)i,"name","形态"+i,"content_json","{}",
                    "prompt","","is_primary",false)).toList();
        });
        var c=context();
        var args=json.createObjectNode();args.putArray("assetKeys").add("p_1");
        Set<String> seen=new HashSet<>();
        while(true) {
            var asset=service.details(c,args).path("items").get(0);
            assertThat(asset.path("variants").size()).isLessThanOrEqualTo(20);
            asset.path("variants").forEach(v->seen.add(v.path("variantKey").asText()));
            if(!asset.path("hasMore").asBoolean())break;
            args.put("cursor",asset.path("nextCursor").asText());
        }
        assertThat(seen).hasSize(51);
    }
}
