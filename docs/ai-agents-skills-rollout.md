# AI Agent/Skill and Model Parameter Rollout

## Release Layout

Each release is stored under `/opt/antv/releases/<release-id>` and contains:

- `backend/ant-short-tv-backend-0.1.0-SNAPSHOT.jar`
- `frontend/dist/`

`/opt/antv/current` is an atomic symlink to the active release. Keep the previous release directories; do not delete them during rollout.

## Deploy

1. Upload the release archive to `/tmp` on the target host.
2. Extract it into a new `/opt/antv/releases/<release-id>` directory and ensure the release is readable by the `ubuntu` service user.
3. Switch the symlink atomically:

   ```bash
   sudo ln -s /opt/antv/releases/<release-id> /opt/antv/current.next
   sudo mv -Tf /opt/antv/current.next /opt/antv/current
   sudo systemctl restart antv.service
   ```

4. Confirm `systemctl is-active antv.service` is `active`, the public model-management page returns `200`, and `/api/currentUser` returns `401` when unauthenticated.
5. Inspect `/opt/antv/shared/logs/backend.log`, `/opt/antv/shared/logs/backend-error.log`, and the AI call-log page for response length, finish reason, truncation, and cost fields. A staging run should use a long script with JSON mode enabled and `max_tokens` above 2048 before production promotion.

## Rollback

Identify the last known-good target, then switch the symlink and restart:

```bash
readlink -f /opt/antv/current
sudo ln -s /opt/antv/releases/<known-good-release> /opt/antv/current.next
sudo mv -Tf /opt/antv/current.next /opt/antv/current
sudo systemctl restart antv.service
systemctl is-active antv.service
```

Database migrations V59-V61 are backward-compatible with the previous application release. Rolling back application code does not require dropping the new tables; disable custom definitions and use the built-in fallback if a configuration revision is problematic.
