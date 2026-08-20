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
npm run frontend:dev
```

Or from the frontend directory:

```powershell
cd frontend
npm install
npm run dev
```

The frontend dev server proxies `/api/*` requests to `http://localhost:8080`.

Useful commands:

```powershell
npm start          # Umi dev server with mock support
npm run dev        # Umi dev server without mocks
npm run lint       # Biome lint and TypeScript check
npm run test       # Vitest
npm run build      # Production build
npm run openapi    # Regenerate generated API services
```

Do not edit `frontend/src/services/ant-design-pro/` directly. Regenerate it with `npm run openapi`.

## Backend

From the repository root:

```powershell
npm run backend:run
```

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

## Development Flow

1. Start the backend from `backend/`.
2. Start the frontend from `frontend/` with `npm run dev`.
3. Keep database schema changes in `backend/src/main/resources/db/migration/`.
4. Run frontend and backend tests before merging changes.

## Verification

```powershell
npm run lint
npm run test

# Or run individual checks:
npm run frontend:lint
npm run frontend:test
npm run backend:test
```
