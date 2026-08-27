# Ant Short TV Deployment Runbook

This runbook deploys the frontend and backend together to the Ant Short TV
internal-test host. It uses versioned releases and an atomic `current` symlink
so the previous release remains available for rollback.

## Prerequisites

- Build from a clean, pushed commit on `master`.
- Use SSH key authentication or an interactive SSH password. Do not store host
  credentials, API keys, or database passwords in this repository.
- Confirm the server has space for a new backend JAR, frontend bundle, and at
  least one retained release.

## Build and Verify

Run the focused billing and commercial tests before deployment, then build both
artifacts:

```powershell
cd frontend
npm test -- src/pages/ai-service-management/billing/index.test.tsx src/pages/commercial/index.test.tsx src/pages/commercial-management/packages/index.test.tsx src/pages/commercial-management/packages/service.test.ts
npm run build

cd ..\backend
mvn -q -DskipTests package
```

Verify the backend artifact is an executable Spring Boot JAR before upload:

```powershell
jar tf backend/target/ant-short-tv-backend-0.1.0-SNAPSHOT.jar | Select-String 'BOOT-INF/classes'
```

The command must return `BOOT-INF/classes`; a plain JAR cannot be started by
`java -jar` and must not be deployed.

If a local backend is running from `backend/target`, package from a temporary
copy of `backend` that excludes `target`; Spring Boot otherwise cannot replace
the running JAR on Windows.

## Release Layout

The host uses this layout:

```text
/opt/antv/releases/<timestamp>-<commit>/backend/ant-short-tv-backend-0.1.0-SNAPSHOT.jar
/opt/antv/releases/<timestamp>-<commit>/backend/env -> /opt/antv/shared/env
/opt/antv/releases/<timestamp>-<commit>/frontend/dist/
/opt/antv/current -> /opt/antv/releases/<timestamp>-<commit>
```

`antv.service` starts the backend JAR from `/opt/antv/current/backend`; Nginx
serves `/opt/antv/current/frontend/dist` and proxies `/api/` to `127.0.0.1:8080`.

## Deploy

1. Archive `frontend/dist` and upload it with the packaged backend JAR to a
   server temporary directory.
2. Verify SHA-256 hashes of both uploaded artifacts.
3. Create a release directory and install the JAR plus extracted frontend files.
   The archive contains a top-level `dist` directory; extract it so the final
   path is `frontend/dist/index.html`.
4. Link the existing shared runtime environment file. Do not copy it into a
   release directory.
5. Atomically update the `current` symlink and restart the backend:

```bash
sudo ln -sfnT /opt/antv/releases/<release> /opt/antv/current
sudo systemctl restart antv.service
```

## Post-Deploy Checks

```bash
systemctl is-active antv.service
readlink -f /opt/antv/current
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/currentUser
curl -k -sS -o /dev/null -w '%{http_code}\n' https://antv.aixmax.cn/
```

Expected results are `active`, the new release path, `401` for the protected
current-user API without a token, and `200` for the frontend homepage. For the
model billing date fix, confirm the deployed billing chunk contains
`isoLocalDateTime`, then publish a future cost-price version through the UI.

## Rollback

If the new release fails a health check, point `current` back to the preceding
release and restart the backend:

```bash
sudo ln -sfnT /opt/antv/releases/<previous-release> /opt/antv/current
sudo systemctl restart antv.service
```

Do not delete the failed release until the rollback is verified. Retain the
current release and at least one prior release; remove older releases only
after checking disk capacity and confirming they are not the rollback target.
