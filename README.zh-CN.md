# Ant Short TV 短剧制作平台

Ant Short TV 是一个短剧制作平台，包含 React 管理前端和 Java 后端服务。

## 目录结构

- `frontend/`：Umi Max、React、TypeScript、antd、ProComponents、Vitest。
- `backend/`：Java 17、Spring Boot、Spring Security、MyBatis-Plus、Flyway。
- `docs/`：产品和技术需求文档。
- `logs/`：本地运行日志。

## 环境要求

- Node.js 22 或更高版本
- npm
- Java 17
- Maven
- MySQL 兼容数据库

## 启动前端

在仓库根目录执行：

```powershell
npm run dev        # 同时启动前端和后端
npm run dev:status # 查看托管进程
npm run dev:stop   # 停止前端和后端
npm run build     # 构建前端生产产物
```

重复执行 `npm run dev` 时会检查 PID 和 8000/8080 端口，发现已有实例会拒绝再次启动。按 `Ctrl+C` 会同时停止前端和后端。

或进入前端目录执行：

```powershell
cd frontend
npm install
npm run dev
```

开发环境中，前端会把 `/api/*` 请求代理到 `http://localhost:8080`。

常用命令：

```powershell
npm run dev        # 启动 Umi 开发服务，使用真实后端
npm run lint       # Biome lint 和 TypeScript 检查
npm run test       # Vitest 测试
npm run test:coverage # Vitest 覆盖率（仅在 frontend/ 目录执行）
npm run build      # 生产构建
npm run openapi    # 重新生成 API 服务代码
```

不要直接编辑 `frontend/src/services/ant-design-pro/`，该目录由 OpenAPI 生成；需要变更时运行 `npm run openapi`。

## 启动后端

在仓库根目录执行：

```powershell
npm run backend:run
```

后端会自动读取根目录 `env` 文件中的数据库、对象存储和 AI 配置。首次使用请根据 `env.example` 创建并填写 `env`；生产环境建议使用正式环境变量或密钥管理服务。

或进入后端目录执行：

```powershell
cd backend
mvn spring-boot:run
```

后端默认监听 `http://localhost:8080`。

常用命令：

```powershell
mvn test
mvn spring-boot:run
```

常用本地地址：

- `http://localhost:8080/swagger-ui.html`
- `http://localhost:8080/v3/api-docs`
- `http://localhost:8080/api/currentUser`

## 开发流程

1. 在仓库根目录使用 `npm run dev` 启动前后端。
2. 使用 `npm run dev:status` 检查运行状态，结束时使用 `Ctrl+C` 或 `npm run dev:stop`。
3. 数据库结构变更统一放在 `backend/src/main/resources/db/migration/`。
4. 合并代码前运行前端和后端测试。

## 验证命令

```powershell
npm run lint
npm run test
npm run verify
npm run verify:release

# 或单独运行：
npm run frontend:lint
npm run frontend:test
npm run backend:test
```

`npm run verify` 会依次执行前端 lint、前后端全量测试和前端生产构建，适合合并或发布前使用。

`npm run verify:release` 会执行完整验证并生成 `.release/` 发布包，其中包含 `frontend/` 静态资源、`backend/ant-short-tv-backend.jar` 和 `manifest.json`。仅重新生成发布包时使用 `npm run package:release`。
