"""Run the narrow image importer against a deployed runtime without deploying code."""
import argparse
import hashlib
import os
from pathlib import Path
import shlex
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("release", type=Path)
    parser.add_argument("images", type=Path)
    parser.add_argument("mode", choices=("check", "apply", "verify"), default="check", nargs="?")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    runtime = root / "runtime"
    runtime.mkdir(exist_ok=True)
    jar = args.release / "backend/ant-short-tv-backend-0.1.0-SNAPSHOT.jar"
    checksum = hashlib.sha256()
    with jar.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            checksum.update(chunk)
    digest = checksum.hexdigest()
    marker = runtime / "release-sha256"
    if marker.exists() and marker.read_text() != digest:
        raise ValueError("Runtime release changed; use a fresh maintenance directory")
    with zipfile.ZipFile(jar) as archive:
        for member in archive.infolist():
            if member.filename.startswith(("BOOT-INF/classes/", "BOOT-INF/lib/")):
                target = (runtime / member.filename).resolve()
                if not target.is_relative_to(runtime.resolve()):
                    raise ValueError("Unsafe archive path")
                if not target.exists():
                    archive.extract(member, runtime)
    marker.write_text(digest)
    env = dict(os.environ)
    for line in Path("/opt/antv/shared/env").read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.removeprefix("export ").split("=", 1)
        if key in ("MYSQL_URL", "MYSQL_USERNAME", "MYSQL_PASSWORD") or key.startswith("OBJECT_STORAGE_"):
            parts = shlex.split(value, comments=True)
            env[key] = parts[0] if parts else ""
    env.pop("JAVA_TOOL_OPTIONS", None)
    classpath = os.pathsep.join((str(root / "overlay"), str(runtime / "BOOT-INF/classes"), str(runtime / "BOOT-INF/lib/*")))
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", classpath, "-d", str(root / "overlay"),
                    str(root / "AssetArtStyleImageService.java"), str(root / "ImportArtStyleImages.java")], check=True, env=env)
    raise SystemExit(subprocess.run(["java", "-Xmx256m", "-Djava.awt.headless=true", "-Duser.timezone=Asia/Shanghai", "-cp", classpath,
                    "com.antshorttv.storage.ImportArtStyleImages", str(args.images), args.mode, str(root / "receipt.json")], env=env, cwd=root).returncode)


if __name__ == "__main__":
    main()
