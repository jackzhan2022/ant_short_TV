# 灵感缩略图回填运行手册

## 执行

部署包含 `V95` 和 `V96` 的后端版本后，由具备 `PLATFORM_INSPIRATION_THUMBNAIL_BACKFILL` 权限的平台管理员调用：

```text
POST /api/platform/inspiration/thumbnail-backfill?limit=20
```

该接口使用现有会话认证与 CSRF 校验。`limit` 可省略；服务会把请求值限制在 `INSPIRATION_THUMBNAIL_BACKFILL_BATCH_SIZE`（默认 20）以内。响应中的 `processed` 和 `failed` 分别表示本批处理成功和失败的记录数。

按小批次重复调用，直到没有待处理记录。已是 `READY` 的记录会跳过；`FAILED` 和空状态记录会在后续调用中重试，缩略图对象路径固定为 `inspiration/creations/{externalId}/thumbnail.jpg`。

## 监控与排障

在每批之后记录响应计数，并查询失败量：

```sql
select thumbnail_status, count(*)
from inspiration_creation
where import_status = 'IMPORTED'
group by thumbnail_status;
```

对 `FAILED` 记录检查 `thumbnail_error`：图片解码、视频编码不兼容或对象存储读取/写入失败都不会影响原始素材的已导入状态。修复底层原因后再次调用回填接口即可重试；不要直接把原始媒体 URL 暴露给画廊以绕过失败。

## 回滚

迁移只新增缩略图列、索引和平台权限，不删除原始对象或业务数据。若需回滚应用版本，保留迁移和缩略图对象，部署前一版服务即可；旧版本会忽略新增列。需要停止资源消耗时，撤销管理员的回填权限或停止调用接口，不要删除仍可能被新版本引用的缩略图对象。
