# 企微客服与「获取帮助」

## 配置方式

平台管理员进入 **管理后台 → 系统管理 → 客服配置**：

1. 在企业微信「客户联系 → 联系我」中创建接待渠道，并下载二维码。
2. 点击「选择二维码」，选择 PNG 或 JPG 图片并检查预览。
3. 点击「保存二维码」。仅选择图片不会改变线上配置。
4. 点击右下角「获取帮助」，使用微信扫码确认添加联系人流程可用。

图片最多 2 MB，单边最多 4096 像素，总像素不超过 1600 万。系统校验图片格式和可解码性，实际二维码是否有效由管理员扫码确认。替换二维码使用相同操作；「移除二维码」需要确认。

未配置二维码时，悬浮入口保留，点击提示「客服暂未开放，请稍后再试」。登录、注册及已登录页面均可使用入口。客服卡片每次打开重新读取配置及签名图片地址，点击外部、关闭按钮或 Escape 可收起。

## 权限与存储

- `PLATFORM_CUSTOMER_SERVICE_VIEW`：查看客服配置及后台页签。
- `PLATFORM_CUSTOMER_SERVICE_EDIT`：保存、替换或移除二维码。
- 两项权限默认授予 `PLATFORM_ADMIN`，与团队媒体上传权限无关。
- 配置存入 `customer_service_config`；图片复用对象存储，路径为 `platform/customer-service/<UUID>.png` 或 `.jpg`。替换和移除只更新生效路径，旧对象不立即删除，避免影响已打开的卡片。

## 接口

| 方法 | 路径 | 权限 / 用途 |
| --- | --- | --- |
| GET | `/api/public/customer-service` | 匿名可读，仅返回 `{ qrCodeUrl }`；响应禁止缓存 |
| GET | `/api/platform/customer-service` | 平台查看权限 |
| PUT | `/api/platform/customer-service` | 平台编辑权限，multipart 字段 `file` |
| DELETE | `/api/platform/customer-service` | 平台编辑权限，移除当前二维码 |

部署时需同时更新前后端。后端启动时 Flyway 执行 `V179__customer_service_configuration.sql`，初始化配置表和权限；对象存储沿用当前系统配置。首次部署后由管理员上传真实二维码即可，无需将二维码写入前端或重新构建。
