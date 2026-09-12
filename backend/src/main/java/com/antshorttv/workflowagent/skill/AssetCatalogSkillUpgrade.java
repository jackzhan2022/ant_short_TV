package com.antshorttv.workflowagent.skill;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Add the catalog protocol to persisted, possibly edited Skills without replacing user content. */
@Component
public class AssetCatalogSkillUpgrade implements ApplicationRunner {
    static final String CODE = "short-drama-asset-recognition-framework";
    static final String MARKER = "<!-- asset-catalog-protocol:v1 -->";
    private final FileSkillRepository repository;

    public AssetCatalogSkillUpgrade(FileSkillRepository repository) { this.repository = repository; }

    @Override public void run(ApplicationArguments args) {
        if (!repository.exists(CODE)) return;
        SkillDocument current = repository.get(CODE);
        if (current.content().contains(MARKER)) return;
        repository.update(CODE, current.content() + "\n\n" + MARKER + "\n" + """
            ## 资产目录检索协议（v1）

            read_current_episode 的资产目录仅为有界候选，不完整不表示资产不存在。
            新建身份前调用 search_script_assets，按当前剧本中的规范名与明确别名检索；
            有精确匹配时复用 assetKey，有歧义时不得猜测合并。
            需要形态或可视设定时调用 read_asset_details，最多 10 个 key；形态每页最多 20 条。
            hasMore 为 true 时按 nextCursor 续页；形态续页只传一个资产 key。
            search_script_assets 每页最多 50 条；游标仅适用于当前运行、作用域和原查询。
            响应预算为 32 KiB，超大详情错误应减少 key 数量或明确报告，不能伪装读取完整。
            遵循 assetScope 和 assetPromptPolicy；按 read_current_episode → 可选检索/详情 →
            save_episode_assets 的顺序执行，正式保存成功后立即结束。
            """, current.revision());
    }
}
