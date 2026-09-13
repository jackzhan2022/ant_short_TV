# 协调记录迁移说明

`V116__script_asset_extraction_coordination.sql` 仅新增协调表，不修改或删除现有资产、形态、绑定、操作或执行记录。

协调表按 `(tenant_id, project_id, script_id)` 保留一条可复用记录。它不采用软删除：释放所有权时更新同一行的 owner 字段、状态和 `released_at`，因此历史协调状态不会产生第二条 active 记录。

唯一约束会让历史或并发重复插入明确失败；迁移本身不会尝试合并、删除或改写重复业务数据。发布回滚可停用新 admission 代码并恢复旧应用版本；保留该 additive 表不会影响旧路径，后续重新发布可继续复用已有协调记录。
