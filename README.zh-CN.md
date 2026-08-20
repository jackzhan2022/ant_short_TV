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
npm run frontend:dev
```

或进入前端目录执行：

```powershell
cd frontend
npm install
npm run dev
```

开发环境中，前端会把 `/api/*` 请求代理到 `http://localhost:8080`。

常用命令：

```powershell
npm start          # 启动 Umi 开发服务，包含 mock
npm run dev        # 启动 Umi 开发服务，不使用 mock
npm run lint       # Biome lint 和 TypeScript 检查
npm run test       # Vitest 测试
npm run build      # 生产构建
npm run openapi    # 重新生成 API 服务代码
```

不要直接编辑 `frontend/src/services/ant-design-pro/`，该目录由 OpenAPI 生成；需要变更时运行 `npm run openapi`。

## 启动后端

在仓库根目录执行：

```powershell
npm run backend:run
```

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

1. 在 `backend/` 启动后端。
2. 在 `frontend/` 使用 `npm run dev` 启动前端。
3. 数据库结构变更统一放在 `backend/src/main/resources/db/migration/`。
4. 合并代码前运行前端和后端测试。

## 验证命令

```powershell
npm run lint
npm run test

# 或单独运行：
npm run frontend:lint
npm run frontend:test
npm run backend:test
```
