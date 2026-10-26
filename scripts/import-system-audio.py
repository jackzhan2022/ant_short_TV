"""Validate a local audio directory, then optionally import it into COS and MySQL.

Dry run: python scripts/import-system-audio.py <directory> --report outputs/system-audio-import.json
Import:  append --apply --env backend/env after deploying migration V163.
Credentials are read locally and never included in the report.
"""

import argparse
from datetime import datetime
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import subprocess
from urllib.parse import urlsplit


MAX_BYTES = 10 * 1024 * 1024
SUPPORTED = {".mp3": "audio/mpeg", ".wav": "audio/wav"}


def inspect_audio(file, root):
    if not file.resolve().is_relative_to(root):
        raise ValueError("文件链接超出素材目录")
    size = file.stat().st_size
    if not 0 < size <= MAX_BYTES:
        raise ValueError("音频必须大于 0 且不超过 10MB")
    with file.open("rb") as stream:
        header = stream.read(12)
    wav = header[:4] == b"RIFF" and header[8:12] == b"WAVE"
    mp3 = header[:3] == b"ID3" or (len(header) >= 2 and header[0] == 255 and header[1] & 224 == 224)
    if not (wav if file.suffix.lower() == ".wav" else mp3):
        raise ValueError("扩展名与音频内容不一致")
    probe = subprocess.run(["ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(file)],
                           capture_output=True, text=True, encoding="utf-8", timeout=30)
    if probe.returncode:
        raise ValueError("ffprobe 无法读取音频")
    metadata = json.loads(probe.stdout)
    streams = metadata.get("streams", [])
    if len(streams) != 1 or streams[0].get("codec_type") != "audio":
        raise ValueError("文件必须仅包含一个音频流")
    duration = Decimal(metadata.get("format", {}).get("duration", streams[0].get("duration", "0")))
    if not duration.is_finite() or not Decimal("0") < duration <= Decimal("999999999"):
        raise ValueError("音频时长无效")
    duration = duration.quantize(Decimal("0.001"))
    if duration <= 0:
        raise ValueError("音频时长不足 1 毫秒")
    with file.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    relative = file.relative_to(root)
    directories = relative.parts[:-1]
    gender = next(("男声" if part in {"男", "男声", "男性"} else "女声"
                   for part in directories if part in {"男", "男声", "男性", "女", "女声", "女性"}), "")
    ages = [age for age in ["少年", "青年", "中年", "老年"] if any(age in part for part in directories)]
    category = " / ".join(part for part in directories if part not in {"男", "男声", "男性", "女", "女声", "女性"})
    if len(file.stem) > 255 or len(category) > 255 or len(relative.as_posix()) > 1000:
        raise ValueError("名称或目录路径过长")
    return {"name": file.stem, "sourcePath": relative.as_posix(), "category": category,
            "gender": gender, "ageGroup": ages[0] if len(ages) == 1 else "",
            "mimeType": SUPPORTED[file.suffix.lower()], "fileSize": size,
            "durationSeconds": str(duration),
            "sha256": digest}


def inventory(root):
    result = {"validated": [], "duplicates": [], "errors": [], "skipped": []}
    seen = {}
    for file in sorted(root.rglob("*")):
        if not file.is_file():
            continue
        if file.suffix.lower() not in SUPPORTED:
            result["skipped"].append(file.relative_to(root).as_posix())
            continue
        try:
            item = inspect_audio(file, root)
            if item["sha256"] in seen:
                result["duplicates"].append({"path": item["sourcePath"], "sameAs": seen[item["sha256"]]})
            else:
                seen[item["sha256"]] = item["sourcePath"]
                result["validated"].append(item)
        except (ValueError, ArithmeticError, OSError, subprocess.SubprocessError) as error:
            result["errors"].append({"path": file.relative_to(root).as_posix(), "error": str(error)})
    return result


def load_env(path):
    values = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if line.strip() and not line.lstrip().startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def apply(root, report, env_file, save):
    import pymysql
    from qcloud_cos import CosConfig, CosS3Client

    env = load_env(env_file)
    database = urlsplit(env["MYSQL_URL"].removeprefix("jdbc:"))
    cos = CosS3Client(CosConfig(Region=env.get("OBJECT_STORAGE_REGION", "ap-guangzhou"),
                               SecretId=env["OBJECT_STORAGE_ACCESS_KEY"], SecretKey=env["OBJECT_STORAGE_SECRET_KEY"],
                               Scheme="https"))
    bucket = env["OBJECT_STORAGE_BUCKET"]
    month = datetime.now().strftime("%Y%m")
    connection = pymysql.connect(host=database.hostname, port=database.port or 3306,
                                 user=env["MYSQL_USERNAME"], password=env["MYSQL_PASSWORD"],
                                 database=database.path.lstrip("/"), charset="utf8mb4", autocommit=False)
    try:
        with connection.cursor() as cursor:
            cursor.execute("select version from flyway_schema_history where version='163' and success=1")
            if not cursor.fetchone():
                raise ValueError("请先通过 Flyway 部署 V163，再录入音频")
            report["imported"] = []
            for item in report["validated"]:
                cursor.execute("select id,storage_path,status from system_audio_asset where content_sha256=%s", (item["sha256"],))
                existing = cursor.fetchone()
                if existing:
                    if existing[2] != "ACTIVE":
                        raise ValueError("相同音频已停用，需先确认是否重新启用")
                    head = cos.head_object(Bucket=bucket, Key=existing[1])
                    if int(head["Content-Length"]) != item["fileSize"]:
                        raise ValueError("已有音频的存储大小与文件不一致")
                    report["imported"].append({"id": existing[0], "path": item["sourcePath"], "reused": True})
                    save()
                    continue
                file = root / item["sourcePath"]
                # Revalidate against changes between the inventory and upload steps.
                if inspect_audio(file, root) != item:
                    raise ValueError("校验后源文件发生变化，请重新运行")
                key = f"materials/0/system_audio/{month}/{item['sha256']}/v1/original{file.suffix.lower()}"
                cos.upload_file(Bucket=bucket, Key=key, LocalFilePath=str(file), ContentType=item["mimeType"],
                                StorageClass="INTELLIGENT_TIERING", EnableMD5=True)
                head = cos.head_object(Bucket=bucket, Key=key)
                if int(head["Content-Length"]) != item["fileSize"] or head["Content-Type"] != item["mimeType"]:
                    raise ValueError("对象存储上传后校验失败")
                cursor.execute("""insert into system_audio_asset
                    (name,category,gender,age_group,source_path,content_sha256,storage_path,mime_type,
                     file_size,duration_seconds,status,created_at,updated_at)
                    values (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,'ACTIVE',now(),now())""",
                    (item["name"], item["category"], item["gender"], item["ageGroup"], item["sourcePath"],
                     item["sha256"], key, item["mimeType"], item["fileSize"], item["durationSeconds"]))
                connection.commit()
                report["imported"].append({"id": cursor.lastrowid, "path": item["sourcePath"], "reused": False})
                save()
    finally:
        connection.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--report", type=Path, default=Path("outputs/system-audio-import.json"))
    parser.add_argument("--env", type=Path, default=Path("backend/env"))
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    root = args.directory.resolve(strict=True)
    if not root.is_dir():
        parser.error("请指定已解压的音频素材目录")
    report = inventory(root)
    args.report.parent.mkdir(parents=True, exist_ok=True)

    def save():
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    save()
    print(json.dumps({key: len(value) for key, value in report.items()}, ensure_ascii=False))
    if report["errors"] or not report["validated"]:
        raise SystemExit("素材校验未通过，请查看报告；未写入数据库或对象存储。")
    if args.apply:
        try:
            apply(root, report, args.env, save)
        except Exception as error:
            # Third-party SDK exceptions can contain signed URLs or credentials.
            report["importFailure"] = type(error).__name__
            save()
            raise SystemExit("录入中断；已完成项已保存到报告，可检查配置与网络后重跑。") from None
        print(f"已完成 {len(report['imported'])} 个音频的录入/去重核验。")


if __name__ == "__main__":
    main()
