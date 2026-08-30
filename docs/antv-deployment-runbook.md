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

## SSH Key Access

Use a dedicated ED25519 key for deployment rather than a personal or GitHub
key. Never commit the private key, add it to a release archive, or copy it to
the server.

Generate the key on the deployment workstation if one does not already exist:

```powershell
ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\antv_prod_ed25519" -C "antv-production-deploy"
```

An administrator must add the generated `.pub` file to the target deployment
user's `~/.ssh/authorized_keys`. On the server, logged in as that user:

```bash
install -d -m 700 ~/.ssh
printf '%s\n' '<public-key-from-antv_prod_ed25519.pub>' >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

Configure a local SSH alias in `~/.ssh/config`; substitute the production host
when it changes, but keep the deploy user and dedicated key explicit:

```sshconfig
Host antv-prod
  HostName 43.138.147.3
  User ubuntu
  IdentityFile ~/.ssh/antv_prod_ed25519
  IdentitiesOnly yes
```

Verify key-only access before building or uploading any release:

```powershell
ssh -o BatchMode=yes antv-prod "hostname && whoami && readlink -f /opt/antv/current"
```

The command must succeed without a password prompt. The deployment user needs
passwordless `sudo` only for creating release directories, updating the
`current` symlink, and restarting `antv.service`.

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
/opt/antv/shared/workflow-skills/<skill-code>/SKILL.md
/opt/antv/current -> /opt/antv/releases/<timestamp>-<commit>
```

`antv.service` starts the backend JAR from `/opt/antv/current/backend`; Nginx
serves `/opt/antv/current/frontend/dist` and proxies `/api/` to `127.0.0.1:8080`.

### Persistent Workflow Skill storage

Agent（新）loads Skill（新）from a writable filesystem root. Production must set
the following absolute path in `/opt/antv/shared/env`; the application refuses
to start with a relative path under the `prod` or `production` profile and also
checks writable atomic replacement during startup.

```dotenv
AI_WORKFLOW_SKILL_ROOT=/opt/antv/shared/workflow-skills
```

Create the directory once with ownership matching `User=` in `antv.service`.
Do not place the root below `/opt/antv/releases` and do not copy release-bundled
sample Skills over it.

```bash
sudo install -d -m 750 -o <service-user> -g <service-group> /opt/antv/shared/workflow-skills
sudo -u <service-user> test -r /opt/antv/shared/workflow-skills
sudo -u <service-user> test -w /opt/antv/shared/workflow-skills
```

Before every release, back up the Skill root together with the database backup.
Keep both artifacts under the same timestamp so Agent associations and Skill
files can be restored consistently.

```bash
backup=/opt/antv/backups/<timestamp>
sudo install -d -m 750 "$backup"
sudo tar -C /opt/antv/shared -czf "$backup/workflow-skills.tar.gz" workflow-skills
# Create and verify the matching MySQL backup using the production database procedure.
sudo tar -tzf "$backup/workflow-skills.tar.gz" >/dev/null
```

## Deploy

Set a release name from the current pushed commit, archive the frontend, and
upload both artifacts into a release-specific temporary directory. Do not reuse
another release's temporary directory.

```powershell
$release = "$(Get-Date -Format yyyyMMddHHmm)-$(git rev-parse --short HEAD)"
$frontendArchive = ".temp\$release-frontend.tar.gz"
tar -czf $frontendArchive -C frontend dist
ssh antv-prod "mkdir -p /tmp/$release"
scp $frontendArchive backend\target\ant-short-tv-backend-0.1.0-SNAPSHOT.jar "antv-prod:/tmp/$release/"
```

Verify that local and remote SHA-256 hashes match before changing any release
link. A mismatch requires deleting only that release-specific temporary
directory and uploading again.

```powershell
Get-FileHash $frontendArchive,backend\target\ant-short-tv-backend-0.1.0-SNAPSHOT.jar -Algorithm SHA256
ssh antv-prod "sha256sum /tmp/$release/*"
```

Install the verified files, retain the existing release for rollback, link the
existing shared runtime environment, then atomically update `current` and
restart the backend. The frontend archive contains a top-level `dist`
directory, so extraction must target `frontend` rather than `frontend/dist`.

```bash
release=/opt/antv/releases/<timestamp>-<commit>
tmp=/tmp/<timestamp>-<commit>
sudo install -d -m 755 "$release/backend" "$release/frontend"
sudo install -m 644 "$tmp/ant-short-tv-backend-0.1.0-SNAPSHOT.jar" "$release/backend/ant-short-tv-backend-0.1.0-SNAPSHOT.jar"
sudo tar -xzf "$tmp/<timestamp>-<commit>-frontend.tar.gz" -C "$release/frontend"
sudo ln -s /opt/antv/shared/env "$release/backend/env"
test -f "$release/frontend/dist/index.html"
sudo ln -sfnT "$release" /opt/antv/current
sudo systemctl restart antv.service
```

## Post-Deploy Checks

On first deployment of Agent（新）/Skill（新）, keep traffic controlled while the
backend starts. Flyway must apply V70 before V71: V70 creates independent Agent,
association, run/audit, and permission structures; V71 creates episode script
versions used by `save_episode_script`. Do not manually run either migration
out of order. Confirm Flyway reaches V71 before deploying the matching frontend.

```bash
systemctl is-active antv.service
readlink -f /opt/antv/current
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/currentUser
curl -k -sS -o /dev/null -w '%{http_code}\n' https://antv.aixmax.cn/
curl -k -sS -D - https://antv.aixmax.cn/api/auth/bootstrap | sed -n '1,20p'
test -d /opt/antv/shared/workflow-skills
sudo -u <service-user> test -w /opt/antv/shared/workflow-skills
```

Expected results are `active`, the new release path, `401` for the protected
current-user API without a token, and `200` for the frontend homepage. The
bootstrap response must declare `application/json;charset=UTF-8` and preserve
Chinese error text. If `/actuator/health` is not exposed, do not treat its 404
as a deployment failure; use the service state and protected API response as
the backend checks.

After signing in as a platform administrator, perform this Agent（新） smoke
test before reopening normal traffic:

1. Confirm the model-management page still shows the five legacy tabs, followed
   by Agent（新） and Skill（新） according to the new independent permissions.
2. Create a Skill with a unique code and valid complete `SKILL.md`; edit its body
   and verify the next detail read returns the new revision without a restart.
3. Create an enabled Agent using a real enabled text model, associate that Skill,
   and allow `read_project_context`, `read_episode_script`,
   `validate_screenplay_format`, and `save_episode_script` as needed.
4. Run the Agent test drawer against a dedicated smoke-test project/episode.
   Verify the step timeline contains linked model calls, allowed tools only,
   redacted inputs/results, and a final output.
5. For a save test, verify a new `script_episode_version` row becomes current,
   the prior version remains queryable, and the Agent run finishes `SUCCESS`.
6. Confirm legacy Agent/Skill pages and an existing legacy script analysis flow
   still load and execute unchanged.

## Rollback

For this additive change, roll back the application first. Hide or disable the
two new frontend tabs/formal-run entry, point `current` to the preceding release,
and restart `antv.service`. Leave V70/V71 tables and
`/opt/antv/shared/workflow-skills` in place; the previous application ignores
them, and retaining them avoids losing configuration or audit evidence.

If the new release fails a health check, point `current` back to the preceding
release and restart the backend:

```bash
sudo ln -sfnT /opt/antv/releases/<previous-release> /opt/antv/current
sudo systemctl restart antv.service
```

Only restore the database and matching `workflow-skills.tar.gz` if configuration
or Skill data itself was corrupted. Stop writes first, restore both from the same
timestamp, verify Agent-to-Skill references, then restart. Never delete the V70
or V71 tables as part of routine application rollback, and never replace the
shared Skill root with files from an older release directory.

Do not delete the failed release until the rollback is verified. Retain the
current release and at least one prior release; remove older releases only
after checking disk capacity and confirming they are not the rollback target.
