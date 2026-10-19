package com.antshorttv.workflowagent.skill;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Add the catalog protocol to persisted, possibly edited Skills without replacing user content. */
@Component
public class AssetCatalogSkillUpgrade implements ApplicationRunner {
    static final String CODE = "short-drama-asset-recognition-framework";
    static final String MARKER = "<!-- asset-catalog-protocol:v3 -->";
    private final FileSkillRepository repository;

    public AssetCatalogSkillUpgrade(FileSkillRepository repository) { this.repository = repository; }

    @Override public void run(ApplicationArguments args) {
        if (!repository.exists(CODE)) return;
        SkillDocument current = repository.get(CODE);
        if (current.content().contains(MARKER)) return;
        repository.update(CODE, current.content() + "\n\n" + MARKER + "\n" + """
            ## 资产目录检索协议（v3，批量查重与详情降级）

            read_current_episode 的资产目录仅为有界候选，不完整不表示资产不存在。
            新建身份前调用 search_script_assets，按当前剧本中的规范名与明确别名检索；
            已有可信候选足够时直接复用，不重复检索。
            同一轮先收集全部缺少可信 key 的名称，优先一次调用 search_script_assets：
            {"queries":[{"assetType":"CHARACTER","query":"原文角色名"},{"assetType":"PROP","query":"原文道具名"}]}。
            queries 每批 1 到 50 项，可混合 CHARACTER、SCENE、PROP；不得把同一轮清单逐个拆成工具调用。
            results 与请求顺序一一对应，每项含 assetType、query、total、items、hasMore、nextCursor。
            total=0 且 hasMore=false 才表示该查询未命中；多个匹配不得任选或自动合并。
            有精确匹配时复用 assetKey，有歧义时不得猜测合并。
            批量 queries 与单项参数不能混用；仅分页、后来发现遗漏或超限拆批时补充调用。
            续页用该结果的 assetType、query、nextCursor 作为单项 cursor，不得重新逐个查整份清单。
            需要形态或可视设定时调用 read_asset_details，最多 10 个 key；形态每页最多 20 条。
            read_asset_details 默认返回完整详情 detailLevel=FULL，仅响应超过预算时在同一次工具调用中自动降级为 SUMMARY。
            SUMMARY 的 degraded=true、degradationReason=RESPONSE_SIZE_LIMIT，contentTruncated 表示内容有省略。
            摘要省略长提示词和证据，只保留标识、名称、外观摘要、默认和本集绑定状态、hasPrompt。
            hasPrompt=true 的已有提示词不能因为摘要中没有 prompt 就重新生成；不得声称已读取省略的全文。
            hasMore 为 true 时按 nextCursor 续页；形态续页只传一个资产 key，不重复读取同页。
            search_script_assets 每页最多 50 条；游标仅适用于当前运行、作用域和原查询。
            响应预算为 32 KiB，批量响应超限时减少 queries 分批；详情默认完整，只有超限才自动返回摘要；摘要仍超限时减少 key 数量，不能伪装读取完整。
            候选和匹配信息已足够时不读取详情；需要补充时合并 read_asset_details，避免重复读取。
            检索完成后及时生成完整结果并保存，为保存及必要纠错保留执行步数。
            遵循 assetScope 和 assetPromptPolicy；按 read_current_episode → 可选检索/详情 →
            save_episode_assets 的顺序执行，正式保存成功后立即结束。
            """, current.revision());
    }
}
