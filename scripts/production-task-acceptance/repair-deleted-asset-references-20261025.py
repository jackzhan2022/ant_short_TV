"""One-off, scoped historical repair. Run beside mysql_check.py on the deployment host.

Default: locked read/preview followed by rollback. --apply: durable backup, exact
row checks, repair, verification and commit. Does not invoke application/model APIs.
"""
import copy
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import uuid
from datetime import datetime, timezone

from mysql_check import connection


SHOT_IDS = [4252, 4253, 4254, 4255, 4256]
REF_IDS = [22211, 22217, 22222, 22228, 22233]
SCOPE = "tenant_id=37 and project_id=66"


def hex_text(value):
    return "convert(0x" + value.encode("utf-8").hex() + " using utf8mb4)"


def save_json(path, value):
    with path.open("x", encoding="utf-8") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.flush()
        os.fsync(stream.fileno())


class Session:
    def __init__(self):
        cmd, env = connection()
        self.errors = tempfile.TemporaryFile(mode="w+t")
        self.process = subprocess.Popen(cmd + ["--unbuffered"], env=env,
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=self.errors,
            text=True, encoding="utf-8")

    def query(self, sql):
        marker = "repair_end_" + uuid.uuid4().hex
        self.process.stdin.write(sql.rstrip(";\n") + ";\nselect '" + marker + "';\n")
        self.process.stdin.flush()
        rows = []
        while True:
            line = self.process.stdout.readline()
            if not line:
                self.errors.seek(0)
                raise RuntimeError(self.errors.read())
            line = line.rstrip("\r\n")
            if line == marker:
                return rows
            rows.append(line)

    def records(self, table, condition, lock=False):
        columns = [row.split("\t")[0] for row in self.query("show columns from " + table)]
        pairs = ",".join("'" + column + "',`" + column + "`" for column in columns)
        sql = "select json_object(" + pairs + ") from " + table + " where " + condition + " order by id"
        return [json.loads(row) for row in self.query(sql + (" for update" if lock else ""))]

    def close(self):
        if self.process.poll() is None:
            try:
                self.query("rollback")
                self.process.stdin.close()
                self.process.wait(timeout=5)
            finally:
                if self.process.poll() is None:
                    self.process.kill()
                    self.process.wait()
        self.errors.close()


def main():
    apply = sys.argv[1:] == ["--apply"]
    assert not sys.argv[1:] or apply, "Only --apply is supported"
    os.umask(0o077)
    signal.alarm(60)
    db = Session()
    backup = None
    try:
        db.query("set session innodb_lock_wait_timeout=5; set session max_execution_time=5000; start transaction")
        assets = db.records("prop_asset", SCOPE + " and id=2315", True)
        assert len(assets) == 1 and assets[0]["deleted_at"] and assets[0]["merge_target_id"] is None
        shots = db.records("storyboard", SCOPE + " and id in (" + ",".join(map(str, SHOT_IDS)) + ")", True)
        refs = db.records("storyboard_asset_reference", SCOPE + " and asset_type='PROP' and asset_id=2315 and retired_at is null", True)
        assert [row["id"] for row in shots] == SHOT_IDS
        assert [row["id"] for row in refs] == REF_IDS
        assert all(row["deleted_at"] is None for row in shots)
        assert all(row["variant_id"] == 5198 and row["storyboard_id"] in SHOT_IDS for row in refs)
        changes = []
        for shot in shots:
            original = json.loads(shot["prompt_document_json"])
            updated = copy.deepcopy(original)
            converted = 0
            for index, node in enumerate(original["nodes"]):
                if node.get("type") == "mention" and node.get("assetType") == "PROP" and node.get("assetId") == 2315:
                    assert node["sourceType"] == "ASSET_VISUAL_VARIANT" and node["sourceId"] == 5198
                    assert node["variantId"] == 5198 and node["displayName"]
                    updated["nodes"][index] = {"type": "text", "text": node["displayName"]}
                    converted += 1
            def visible(document):
                return "".join(node["text"] if node["type"] == "text" else node["displayName"] for node in document["nodes"])
            assert visible(original) == visible(updated)
            assert converted == (5 if shot["id"] == 4256 else 1)
            changes.append({"id": shot["id"], "converted": converted,
                "document": json.dumps(updated, ensure_ascii=False, separators=(",", ":"))})
        summary = {"projectId": 66, "assetId": 2315, "storyboards": len(shots),
            "retiredReferences": len(refs), "convertedMentions": sum(row["converted"] for row in changes),
            "visibleTextPreserved": True, "mode": "apply" if apply else "preview"}
        if not apply:
            db.query("rollback")
            print(json.dumps(summary, ensure_ascii=False))
            return
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        backup = Path("/opt/antv/backups") / (stamp + "-deleted-asset-text-repair")
        backup.mkdir(mode=0o700)
        save_json(backup / "before.json", {"assets": assets, "storyboards": shots, "references": refs})
        save_json(backup / "plan.json", {"summary": summary, "changes": changes})
        # The backup must be readable and complete before the first UPDATE.
        assert json.loads((backup / "before.json").read_text()) == {"assets": assets, "storyboards": shots, "references": refs}
        for change in changes:
            count = db.query("update storyboard set prompt_document_json=" + hex_text(change["document"])
                + ",updated_at=now() where " + SCOPE + " and deleted_at is null and id=" + str(change["id"])
                + ";select row_count()")
            assert count == ["1"], count
        count = db.query("update storyboard_asset_reference set retired_at=now(),updated_at=now() where "
            + SCOPE + " and asset_type='PROP' and asset_id=2315 and retired_at is null and id in ("
            + ",".join(map(str, REF_IDS)) + ");select row_count()")
        assert count == ["5"], count
        after_shots = db.records("storyboard", SCOPE + " and id in (" + ",".join(map(str, SHOT_IDS)) + ")")
        after_refs = db.records("storyboard_asset_reference", SCOPE + " and id in (" + ",".join(map(str, REF_IDS)) + ")")
        for before, after, change in zip(shots, after_shots, changes):
            assert after["prompt_document_json"] == change["document"]
            assert {k: v for k, v in before.items() if k not in ("prompt_document_json", "updated_at")} == {
                k: v for k, v in after.items() if k not in ("prompt_document_json", "updated_at")}
        for before, after in zip(refs, after_refs):
            assert after["retired_at"] is not None
            assert {k: v for k, v in before.items() if k not in ("retired_at", "updated_at", "active_order_marker")} == {
                k: v for k, v in after.items() if k not in ("retired_at", "updated_at", "active_order_marker")}
        assert db.records("prop_asset", SCOPE + " and id=2315") == assets
        save_json(backup / "after.json", {"storyboards": after_shots, "references": after_refs})
        summary["backup"] = str(backup)
        summary["backupSha256"] = hashlib.sha256((backup / "before.json").read_bytes()).hexdigest()
        db.query("commit")
        summary["committed"] = True
        save_json(backup / "receipt.json", summary)
        print(json.dumps(summary, ensure_ascii=False))
    finally:
        db.close()


if __name__ == "__main__":
    main()
