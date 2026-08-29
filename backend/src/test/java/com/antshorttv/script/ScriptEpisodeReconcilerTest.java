package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScriptEpisodeReconcilerTest {

    @Test
    void reconcilesExplicitHeadingsBeforeChangedContent() throws Exception {
        Object result = reconcile(
            List.of(existing(1L, "stable-rain", 1, "第1集：雨夜", "旧正文")),
            List.of(new ScriptEpisodeResponse(1, "第1集：雨夜", "完全改写后的正文"))
        );

        assertThat(stableKeys(result)).containsExactly("stable-rain");
        assertThat(statuses(result)).containsExactly("HEADING_MATCHED");
    }

    @Test
    void keepsIdentityForUnchangedIntelligentSplits() throws Exception {
        Object result = reconcile(
            List.of(existing(1L, "stable-anchor", 1, "第1集", "主角回家并发现秘密。")),
            List.of(new ScriptEpisodeResponse(1, "第1集", "主角回家并发现秘密。"))
        );

        assertThat(stableKeys(result)).containsExactly("stable-anchor");
        assertThat(statuses(result)).containsExactly("CONTENT_MATCHED");
    }

    @Test
    void keepsIdentityWhenGenericEpisodeContentChangesWithoutChangingEpisodeSet() throws Exception {
        Object result = reconcile(
            List.of(existing(1L, "stable-one", 1, "第1集", "旧正文")),
            List.of(new ScriptEpisodeResponse(1, "第1集", "修改后的正文"))
        );

        assertThat(stableKeys(result)).containsExactly("stable-one");
        assertThat(statuses(result)).containsExactly("POSITION_MATCHED");
        assertThat(retiredKeys(result)).isEmpty();
    }

    @Test
    void keepsIdentityWhenGenericHeadingBecomesDescriptiveAndContentChanges() throws Exception {
        Object result = reconcile(
            List.of(existing(1L, "stable-one", 1, "第1集", "旧正文")),
            List.of(new ScriptEpisodeResponse(1, "第1集：雨夜", "修改后的正文"))
        );

        assertThat(stableKeys(result)).containsExactly("stable-one");
        assertThat(statuses(result)).containsExactly("POSITION_MATCHED");
        assertThat(retiredKeys(result)).isEmpty();
    }

    @Test
    void preservesIdentitiesWhenEpisodesAreReordered() throws Exception {
        Object result = reconcile(
            List.of(
                existing(1L, "stable-a", 1, "第1集", "A剧情"),
                existing(2L, "stable-b", 2, "第2集", "B剧情")
            ),
            List.of(
                new ScriptEpisodeResponse(1, "第1集", "B剧情"),
                new ScriptEpisodeResponse(2, "第2集", "A剧情")
            )
        );

        assertThat(stableKeys(result)).containsExactly("stable-b", "stable-a");
    }

    @Test
    void createsAddedEpisodesAndRetiresRemovedEpisodes() throws Exception {
        Object result = reconcile(
            List.of(
                existing(1L, "stable-a", 1, "第1集：A", "A剧情"),
                existing(2L, "stable-b", 2, "第2集：B", "B剧情")
            ),
            List.of(
                new ScriptEpisodeResponse(1, "第1集：A", "A剧情"),
                new ScriptEpisodeResponse(2, "第2集：C", "C剧情")
            )
        );

        assertThat(stableKeys(result).get(0)).isEqualTo("stable-a");
        assertThat(stableKeys(result).get(1)).startsWith("episode-");
        assertThat(retiredKeys(result)).containsExactly("stable-b");
    }

    @Test
    void refusesToInheritAnAmbiguousHeading() throws Exception {
        Object result = reconcile(
            List.of(
                existing(1L, "stable-a", 1, "第1集：秘密", "旧剧情A"),
                existing(2L, "stable-b", 2, "第2集：秘密", "旧剧情B")
            ),
            List.of(new ScriptEpisodeResponse(1, "第1集：秘密", "无法锚定的新剧情"))
        );

        assertThat(stableKeys(result).get(0)).startsWith("episode-");
        assertThat(statuses(result)).containsExactly("AMBIGUOUS");
        assertThat(retiredKeys(result)).containsExactlyInAnyOrder("stable-a", "stable-b");
    }

    private Object reconcile(List<Object> existing, List<ScriptEpisodeResponse> drafts) throws Exception {
        Class<?> type = load("com.antshorttv.script.ScriptEpisodeReconciler");
        assertThat(type).isNotNull();
        Object reconciler = type.getDeclaredConstructor().newInstance();
        Method method = type.getDeclaredMethod("reconcile", List.class, List.class);
        return method.invoke(reconciler, existing, drafts);
    }

    private Object existing(Long id, String stableKey, Integer episodeNo, String title, String content) throws Exception {
        Class<?> type = load("com.antshorttv.script.ScriptEpisodeReconciler$ExistingEpisode");
        assertThat(type).isNotNull();
        Constructor<?> constructor = type.getDeclaredConstructor(Long.class, String.class, Integer.class, String.class, String.class);
        return constructor.newInstance(id, stableKey, episodeNo, title, content);
    }

    @SuppressWarnings("unchecked")
    private List<String> stableKeys(Object result) throws Exception {
        return ((List<Object>) result.getClass().getMethod("active").invoke(result)).stream()
            .map(item -> invokeString(item, "stableKey"))
            .toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> statuses(Object result) throws Exception {
        return ((List<Object>) result.getClass().getMethod("active").invoke(result)).stream()
            .map(item -> invokeString(item, "status"))
            .toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> retiredKeys(Object result) throws Exception {
        return (List<String>) result.getClass().getMethod("retiredStableKeys").invoke(result);
    }

    private String invokeString(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target).toString();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }
}
