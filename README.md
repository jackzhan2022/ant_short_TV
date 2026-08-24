# Ant Short TV

Ant Short TV is a short-drama production platform with a React admin frontend and a Java backend.

## Project Structure

- `frontend/`: Umi Max, React, TypeScript, antd, ProComponents, Vitest.
- `backend/`: Java 17, Spring Boot, Spring Security, MyBatis-Plus, Flyway.
- `docs/`: product and technical requirement documents.
- `logs/`: local runtime logs.

## Prerequisites

- Node.js 22 or later
- npm
- Java 17
- Maven
- MySQL-compatible database for local backend development

## Frontend

From the repository root:

```powershell
npm run dev        # Start frontend and backend together
npm run dev:status # Show managed processes
npm run dev:stop   # Stop frontend and backend
npm run build     # 构建前端生产产物
```

Repeated `npm run dev` calls are rejected when managed PIDs or ports 8000/8080 are already active. `Ctrl+C` stops both services.

Or from the frontend directory:

```powershell
cd frontend
npm install
npm run dev
```

The frontend dev server proxies `/api/*` requests to `http://localhost:8080`.

Useful commands:

```powershell
npm run dev        # Umi dev server with the real backend
npm run lint       # Biome lint and TypeScript check
npm run test       # Vitest
npm run test:coverage # Vitest coverage (run inside frontend/)
npm run build      # Production build
npm run openapi    # Regenerate generated API services
```

Do not edit `frontend/src/services/ant-design-pro/` directly. Regenerate it with `npm run openapi`.

## Backend

From the repository root:

```powershell
npm run backend:run
```

The backend automatically imports the repository-root `env` file for database, object storage, and AI settings. Create it from `env.example` for local development; use deployment environment variables or a secret manager in production.

Or from the backend directory:

```powershell
cd backend
mvn spring-boot:run
```

The backend listens on `http://localhost:8080`.

Useful commands:

```powershell
mvn test
mvn spring-boot:run
```

Useful local URLs:

- `http://localhost:8080/swagger-ui.html`
- `http://localhost:8080/v3/api-docs`
- `http://localhost:8080/api/currentUser`

Object storage is enabled by default for backend file storage and reads. Configure MinIO/S3 through environment variables:

```powershell
$env:OBJECT_STORAGE_MODE = "s3"
$env:OBJECT_STORAGE_ENDPOINT = "https://minio.aixmax.cn"
$env:OBJECT_STORAGE_BUCKET = "ant-short-tv"
$env:OBJECT_STORAGE_ACCESS_KEY = "<access-key>"
$env:OBJECT_STORAGE_SECRET_KEY = "<secret-key>"
```

The backend creates the bucket automatically when `OBJECT_STORAGE_AUTO_CREATE_BUCKET` is `true`.

## Development Flow

1. Run `npm run dev` from the repository root to start both services.
2. Use `npm run dev:status` to inspect them and `Ctrl+C` or `npm run dev:stop` to stop them.
3. Keep database schema changes in `backend/src/main/resources/db/migration/`.
4. Run frontend and backend tests before merging changes.

## Verification

```powershell
npm run lint
npm run test
npm run verify
npm run verify:release

# Or run individual checks:
npm run frontend:lint
npm run frontend:test
npm run backend:test
```

`npm run verify` runs frontend lint, the full frontend/backend test suite, and the frontend production build. Use it before merging or releasing.

`npm run verify:release` performs the full verification and creates a `.release/` bundle containing frontend assets, `backend/ant-short-tv-backend.jar`, and `manifest.json`. Use `npm run package:release` when only the release bundle needs to be rebuilt.
