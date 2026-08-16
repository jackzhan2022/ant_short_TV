# Ant Short TV

This repository is split into two top-level application directories:

- `frontend/`: Ant Design Pro frontend based on Umi Max, React, antd, and TypeScript.
- `backend/`: Java 17 Spring Boot backend service.

## Start Backend

```powershell
cd backend
mvn spring-boot:run
```

The backend listens on `http://localhost:8080`.

Useful backend URLs:

- `http://localhost:8080/api/currentUser`
- `http://localhost:8080/swagger-ui.html`
- `http://localhost:8080/v3/api-docs`

## Start Frontend

```powershell
cd frontend
npm install
npm run dev
```

The frontend proxies `/api/*` requests to `http://localhost:8080` in development.

Use `npm start` only when you want Umi mock behavior. Use `npm run dev` when connecting to the Java backend.
