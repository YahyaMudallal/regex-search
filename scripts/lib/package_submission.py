#!/usr/bin/env python3
"""Archive de rendu déterministe construite depuis git ls-files + JAR final."""

from __future__ import annotations
import hashlib
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
MAX_BYTES = 10 * 1024 * 1024
EXCLUDED_PREFIXES = (".github/modernize/",)
EXCLUDED_PARTS = {"__pycache__", ".pytest_cache", ".mypy_cache", ".ruff_cache"}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def tracked_files() -> list[Path]:
    raw = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
    result = []
    for item in raw.decode("utf-8").split("\0"):
        if not item:
            continue
        if item.startswith(EXCLUDED_PREFIXES):
            continue
        path = Path(item)
        if any(part in EXCLUDED_PARTS for part in path.parts):
            continue
        source = ROOT / path
        if source.is_symlink():
            raise ValueError(f"Lien symbolique refusé dans le rendu : {path}")
        if source.is_file():
            result.append(path)
    return sorted(result, key=lambda p: p.as_posix())


def add_bytes(archive: zipfile.ZipFile, name: str, data: bytes, executable: bool = False) -> None:
    info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    info.create_system = 3
    mode = 0o755 if executable else 0o644
    info.external_attr = mode << 16
    archive.writestr(info, data, compresslevel=9)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: package_submission.py <jar>")
    jar = Path(sys.argv[1]).resolve()
    if not jar.is_file():
        raise ValueError("JAR final introuvable")
    dist = ROOT / "dist"
    if dist.exists():
        shutil.rmtree(dist)
    dist.mkdir()
    final_jar = dist / "regex-search.jar"
    shutil.copyfile(jar, final_jar)

    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    tracked = tracked_files()
    manifest = [
        "regex-search submission",
        f"git_head={head}",
        "java_release=21",
        f"jar_sha256={sha256(final_jar)}",
        f"tracked_files={len(tracked)}",
        "archive_policy=git tracked files + regex-search.jar; no .git/target/venv/cache",
        "",
    ]
    archive_path = dist / "regex-search-submission.zip"
    with zipfile.ZipFile(archive_path, "w") as archive:
        for relative in tracked:
            source = ROOT / relative
            executable = bool(source.stat().st_mode & 0o111)
            add_bytes(archive, f"regex-search/{relative.as_posix()}", source.read_bytes(), executable)
        add_bytes(archive, "regex-search/regex-search.jar", final_jar.read_bytes())
        add_bytes(archive, "regex-search/SUBMISSION-MANIFEST.txt", "\n".join(manifest).encode("utf-8"))
    size = archive_path.stat().st_size
    if size > MAX_BYTES:
        archive_path.unlink()
        raise ValueError(f"Archive trop lourde : {size / 1024**2:.2f} Mio > 10 Mio")
    print(f"Archive : {archive_path.relative_to(ROOT)} ({size / 1024**2:.2f} Mio)")
    print(f"JAR : {final_jar.relative_to(ROOT)} · sha256 {sha256(final_jar)}")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        print(f"ERREUR : {error}", file=sys.stderr)
        raise SystemExit(1)
