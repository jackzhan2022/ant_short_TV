package com.antshorttv.style;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** Asset-only art rules. Storyboard skills and shared episode contexts never consume these rules. */
@Service
public class AssetArtStyleService {
    public static final String RULES_VERSION = "toonflow-adapted-1";
    private final ProjectMapper projects;
    private final ObjectMapper json;
    private final List<Style> styles;

    public AssetArtStyleService(ProjectMapper projects, ObjectMapper json) {
        this.projects = projects;
        this.json = json;
        try (var input = new ClassPathResource("art-styles/toonflow-adapted-1.json").getInputStream()) {
            styles = List.of(json.readValue(input, Style[].class));
        } catch (IOException e) {
            throw new IllegalStateException("画风 2.0 规则加载失败。", e);
        }
    }

    public List<Style> catalog() { return styles; }

    public void configure(ProjectEntity project, String version, String code, String legacyName) {
        String resolved = version == null ? Objects.requireNonNullElse(project.visualStyleVersion, "V1") : version;
        if (!List.of("V1", "V2").contains(resolved)) throw invalid("画风版本必须为 V1 或 V2。");
        if ("V2".equals(resolved)) {
            Style style = requireStyle(code == null ? project.visualStyleCode : code);
            project.visualStyle = style.name();
            project.visualStyleCode = style.code();
        } else {
            if ("V2".equals(project.visualStyleVersion) && (legacyName == null || legacyName.isBlank()))
                throw invalid("请选择画风 1.0 风格。");
            project.visualStyleCode = "";
            if (legacyName != null) project.visualStyle = legacyName;
        }
        project.visualStyleVersion = resolved;
    }

    /** Store the actual rule content so retries also survive future catalog revisions. */
    public String snapshot(Long tenantId, Long projectId) {
        ProjectEntity project = projects.selectById(projectId);
        if (project == null || !Objects.equals(project.tenantId, tenantId) || project.deletedAt != null)
            throw invalid("项目不存在。");
        Style style = "V2".equals(project.visualStyleVersion) ? requireStyle(project.visualStyleCode) : null;
        try {
            return json.writeValueAsString(new Snapshot(style == null ? "V1" : "V2", RULES_VERSION, style));
        } catch (IOException e) {
            throw new IllegalStateException("无法保存画风快照。", e);
        }
    }

    public String rules(String snapshot, String scope) {
        if (snapshot == null || snapshot.isBlank() || "STORYBOARD".equals(scope)) return "";
        if (!List.of("ALL", "ASSETS", "CHARACTER", "SCENE", "PROP").contains(scope))
            throw invalid("不支持的资产画风范围。");
        try {
            Snapshot frozen = json.readValue(snapshot, Snapshot.class);
            if ("V1".equals(frozen.version())) return "";
            if (!"V2".equals(frozen.version()) || frozen.style() == null)
                throw new IllegalStateException("画风快照损坏。");
            Style style = frozen.style();
            StringBuilder rules = new StringBuilder("\n【资产画风 2.0：")
                .append(style.name()).append("；规则版本：").append(frozen.rulesVersion()).append("】\n")
                .append("仅用于资产提示词的视觉表达，不改变原文事实、既有身份、资产识别范围、证据、输出字段和提示词覆盖策略。")
                .append("现有资产展示格式优先于美术建议；年代、地域、年龄与物件以剧本和可信设定为准。")
                .append("不得加入分镜、运镜、剧情动作或视频指令；不得自动更换模型。\n")
                .append(style.common()).append('\n');
            if (all(scope) || "CHARACTER".equals(scope)) rules.append("角色美术：").append(style.character())
                .append(" 保留现有左侧40%特写、右侧60%正侧背全身三视图与中性站姿。换装仅输出有依据的视觉增量，不重复画风、构图，不重新设计脸。\n");
            if (all(scope) || "SCENE".equals(scope)) rules.append("场景美术：").append(style.scene())
                .append(" 保留现有正视、俯视、背视、侧视静态四宫格，不改为单幅或前右后左四向。无人无动物，固定结构、时段与世界光源一致。\n");
            if (all(scope) || "PROP".equals(scope)) rules.append("道具美术：").append(style.prop())
                .append(" 沿用现有道具固定展示要求和白底规范，不加入手、人物或剧情环境。状态变体保持结构与识别特征，仅写可见变化。\n");
            return rules.toString();
        } catch (IOException e) {
            throw new IllegalStateException("无法读取画风快照。", e);
        }
    }

    public static List<String> backfillScopes(String target, String artRules) {
        return "ALL".equals(target) && !artRules.isBlank() ? List.of("ASSETS", "STORYBOARD") : List.of(target);
    }

    private boolean all(String scope) { return "ALL".equals(scope) || "ASSETS".equals(scope); }
    private Style requireStyle(String code) {
        return styles.stream().filter(style -> style.code().equals(code)).findFirst()
            .orElseThrow(() -> invalid("请选择有效的画风 2.0 风格。"));
    }
    private BusinessException invalid(String message) { return new BusinessException(ErrorCode.VALIDATION_ERROR, message); }

    public record Style(String code, String name, String category, String description,
                        String common, String character, String scene, String prop) {}
    public record Snapshot(String version, String rulesVersion, Style style) {}
}
