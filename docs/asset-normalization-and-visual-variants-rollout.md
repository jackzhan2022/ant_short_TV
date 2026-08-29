# 资产归一化与剧集视觉形象发布手册

## 发布顺序与开关

本变更采用加法迁移，数据库结构和双读兼容必须先于 UI 发布：

1. 备份生产库并执行 Flyway V63–V69。
2. 发布后端，保持旧 `main_image_result_id/main_image_url` 字段可读写。
3. 校验回填和孤儿数据后，开放审核队列与视觉形象管理入口。
4. 最后开放剧集绑定，并让分镜和媒体生成通过统一解析器取图。

当前代码没有用环境变量绕过归一化写入；回滚开关应放在前端路由或发布平台：分别隐藏“识别结果审核”“视觉形象管理”“剧集绑定”三个入口。隐藏 UI 不影响旧工作区使用，后端新增接口和表保持在线。不得切回“AI 结果直接写正式资产”的旧行为。

## 回填与验收

V65 会为每个未删除且具有可用旧图片引用的角色、场景、道具创建一个 `LEGACY_BACKFILL` 主形象，不改变正式资产 ID 或旧字段。迁移后执行：

V66 修复内置 Agent 的初始提示词与技能关联，V67 为已有剧本回填稳定剧集，V68 为文本调用日志增加 `response_length`、`finish_reason`、`truncated` 三个诊断字段，V69 将分析 Agent 的持久化提示词与运行时契约对齐。

```sql
select asset_type, count(*)
from asset_visual_variant
where source_type = 'LEGACY_BACKFILL' and deleted_at is null
group by asset_type;

select 'CHARACTER' asset_type, count(*) orphan_count
from asset_visual_variant v left join character_asset a on a.id = v.asset_id
 and a.tenant_id = v.tenant_id and a.project_id = v.project_id
where v.asset_type = 'CHARACTER' and (a.id is null or a.deleted_at is not null)
union all
select 'SCENE', count(*) from asset_visual_variant v left join scene_asset a on a.id = v.asset_id
 and a.tenant_id = v.tenant_id and a.project_id = v.project_id
where v.asset_type = 'SCENE' and (a.id is null or a.deleted_at is not null)
union all
select 'PROP', count(*) from asset_visual_variant v left join prop_asset a on a.id = v.asset_id
 and a.tenant_id = v.tenant_id and a.project_id = v.project_id
where v.asset_type = 'PROP' and (a.id is null or a.deleted_at is not null);
```

孤儿数必须为 0。旧图片存在但未回填的资产也必须为 0：

```sql
select count(*) missing_backfill
from character_asset a
where a.deleted_at is null
  and (a.main_image_result_id is not null or a.main_image_url is not null)
  and not exists (select 1 from asset_visual_variant v where v.asset_type = 'CHARACTER'
    and v.asset_id = a.id and v.tenant_id = a.tenant_id and v.project_id = a.project_id
    and v.deleted_at is null);
```

场景和道具使用相同查询替换表名与 `asset_type`。

## 剧集歧义与绑定诊断

```sql
select count(*) scripts_without_stable_episodes
from script s
where s.deleted_at is null and s.current_version_id is not null
  and not exists (select 1 from script_episode e
    where e.tenant_id = s.tenant_id and e.project_id = s.project_id
      and e.script_id = s.id and e.retired_at is null);

select project_id, script_id, episode_no, title, reconciliation_status
from script_episode
where status = 'NEEDS_REVIEW' and retired_at is null
order by project_id, episode_no;

select b.project_id, b.asset_type, b.asset_id, b.variant_id, b.episode_id, b.binding_status
from asset_visual_variant_episode b
left join script_episode e on e.id = b.episode_id and e.tenant_id = b.tenant_id
where b.retired_at is null and (e.id is null or e.retired_at is not null
  or b.binding_status = 'REVIEW_REQUIRED');
```

剧集退役时绑定会标记为 `REVIEW_REQUIRED`，不会静默绑定到新剧集。运营人员应确认新旧剧集关系后重新选择稳定剧集 ID。

## 观测指标

- 归一化运行：按 `status` 统计 `script_asset_normalization_run`，关注 `FAILED` 和 `PARTIAL_SUCCESS`。
- 候选质量：按 `validation_status/review_status/asset_type` 统计 `script_asset_candidate`。
- 合并质量：按 `decision_type/status` 统计 `script_asset_promotion_decision`，抽样检查低置信度合并。
- 分集稳定性：统计 `script_episode.reconciliation_status = 'AMBIGUOUS'` 和退役数量。
- 生成质量：按 `asset_visual_variant.generation_status` 统计失败率和长时间 `GENERATING`。
- 旧字段回退：监控 `EpisodeAwareVisualResolver.legacyFallbackCount()`；发布后该计数应持续下降。若上升，先查缺失主形象或不可用生成结果，不要删除旧字段。
- 文本输出完整性：检查 `ai_call_log.finish_reason = 'length'` 或 `truncated = true` 的调用；结合 `response_length` 判断是否需要调整模型参数或拆分任务。

```sql
select id, business_scene, model, response_length, finish_reason, truncated, created_at
from ai_call_log
where service_type = 'TEXT' and (truncated = true or finish_reason = 'length')
order by created_at desc
limit 100;
```

## 非破坏性回滚

1. 隐藏三个新 UI 入口并停止创建新绑定。
2. 后端继续运行，解析器仍按“剧集首选 → 主形象 → 旧字段”顺序返回图片。
3. 不回滚 V63–V69，不删除归一化、候选、视觉形象、绑定或调用日志诊断字段。
4. 已选为主形象的图片仍同步到旧资产字段，因此旧客户端可以继续读取。
5. 修复问题后重新开放入口；依靠幂等运行键和决策键重试，不手工复制正式资产。

只有在旧字段回退计数长期为 0、孤儿查询为 0、歧义均处理完成后，才能另开变更移除旧字段兼容。
