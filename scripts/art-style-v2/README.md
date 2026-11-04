# 画风 2.0 图片导入

此工具复用部署包中的存储组件，运行独立维护上下文；不扫描业务组件，不执行 Flyway，不启动业务调度，不部署应用。生产环境读取 `/opt/antv/shared/env` 的数据库和对象存储配置，云凭证沿用实例角色。不输出密钥或签名 URL。

在独立临时目录准备：

- `run-import.py`、`ImportArtStyleImages.java`。
- `backend/src/main/java/com/antshorttv/style/AssetArtStyleImageService.java` 复制为同目录 `AssetArtStyleImageService.java`。
- `backend/src/main/resources/art-styles/preview-images-v2.json` 复制到 `overlay/art-styles/preview-images-v2.json`。
- `images/` 放入清单对应的 11 张原始 PNG，文件名为 `{code}.png`。

在有 Java 17 JDK、Python 3.9+ 的生产主机执行（部署目录参数固定为要复用的版本）：

```sh
python3 run-import.py /opt/antv/releases/<release> ./images check
python3 run-import.py /opt/antv/releases/<release> ./images apply
python3 run-import.py /opt/antv/releases/<release> ./images verify
```

默认模式为 `check`，只核对完整源文件摘要。`apply` 上传缺失原图并提交压缩；已存在且一致的 READY/PENDING 对象复用，FAILED 展示图可重试。若回调尚未完成，退出非零并提示稍后运行 `verify`。不要并发执行导入。

`verify` 检查媒体状态、COS 元数据及带应用 Referer 的签名 CDN GET，验证图片可解码且字节数匹配；生成不含签名 URL 的 `receipt.json`。全部已就绪时重复 `apply` 还会核对压缩任务 ID、重试次数和状态不变。压缩失败需先检查 CI 回调与现有生产媒体服务日志。

样图使用独立 `STYLE_LIBRARY_V2` 命名空间，固定 ID 及原图 SHA-256；不修改旧画风记录。更新已审核图片需同步更新清单并重新导入。导入图片完成不代表新 API 已发布，必须先准备资源、发布后端，再发布引用新接口的前端。
