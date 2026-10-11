"""Run the reviewed price publisher using a staged release, without logging secrets."""
import argparse
import hashlib
import os
from pathlib import Path
import shlex
import subprocess
import tempfile
import zipfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("release", type=Path)
    parser.add_argument("mode", choices=("check", "apply", "verify", "rollback"))
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    jar = args.release / "backend/ant-short-tv-backend-0.1.0-SNAPSHOT.jar"
    checksum = hashlib.sha256()
    with jar.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            checksum.update(chunk)
    digest = checksum.hexdigest()
    classes = root / ("runtime-" + digest)
    if not classes.exists():
        staging = Path(tempfile.mkdtemp(prefix="runtime-extract-", dir=root))
        with zipfile.ZipFile(jar) as archive:
            for member in archive.infolist():
                if member.filename.startswith(("BOOT-INF/classes/", "BOOT-INF/lib/")):
                    target = (staging / member.filename).resolve()
                    if not target.is_relative_to(staging.resolve()):
                        raise ValueError("Unsafe archive path")
                    archive.extract(member, staging)
        (staging / "sha256.complete").write_text(digest)
        staging.rename(classes)
    if (classes / "sha256.complete").read_text() != digest:
        raise ValueError("Staged runtime does not match release JAR")
    values = {}
    for line in Path("/opt/antv/shared/env").read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.removeprefix("export ").split("=", 1)
        if key in ("MYSQL_URL", "MYSQL_USERNAME", "MYSQL_PASSWORD"):
            parts = shlex.split(value, comments=True)
            values[key] = parts[0] if parts else ""
    env = dict(os.environ, **values)
    env.pop("JAVA_TOOL_OPTIONS", None)
    classpath = os.pathsep.join((str(classes / "BOOT-INF/classes"), str(classes / "BOOT-INF/lib/*")))
    command = ["java", "-Xmx256m", "-Duser.timezone=Asia/Shanghai", "--class-path", classpath,
               str(root / "PublishBilling.java"), str(root / "model-billing-20261011.json"),
               args.mode, str(root / "receipt.json")]
    raise SystemExit(subprocess.run(command, env=env, cwd=root).returncode)


if __name__ == "__main__":
    main()
