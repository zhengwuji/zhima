#!/usr/bin/env python3
"""
生成 Android 可用的 release 密钥库，并打印 GitHub Secrets 需要的 base64。

用法（在仓库根目录）：
    python tools/gen_keystore.py                # 生成 keystore/sesame-release.jks
    python tools/gen_keystore.py --print-secrets # 只打印已存在密钥库的 base64

生成后：
  1. 把输出的 4 个值填进 GitHub → Settings → Secrets and variables → Actions
     （SESAME_KEYSTORE_BASE64 / SESAME_STORE_PASSWORD / SESAME_KEY_ALIAS / SESAME_KEY_PASSWORD）
  2. 本地构建时不需要做任何事：app/build.gradle 会读 signing.properties（本文件同目录生成）
"""

import argparse
import base64
import os
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
KEYSTORE = ROOT / "keystore" / "sesame-release.jks"
DEFAULT_ALIAS = "sesame"
DEFAULT_PASSWORD = "sesame-release-2026"
DNAME = "CN=Sesame-M Self Build, OU=Personal, O=Personal, L=Beijing, ST=Beijing, C=CN"


def find_keytool() -> str:
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = pathlib.Path(java_home) / "bin" / ("keytool.exe" if os.name == "nt" else "keytool")
        if candidate.exists():
            return str(candidate)
    return "keytool"


def create_keystore(alias: str, password: str) -> None:
    if KEYSTORE.exists():
        print(f"密钥库已存在，跳过生成：{KEYSTORE}")
        return
    KEYSTORE.parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        find_keytool(), "-genkeypair", "-v",
        "-keystore", str(KEYSTORE),
        "-alias", alias,
        "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
        "-storepass", password, "-keypass", password,
        "-dname", DNAME,
    ]
    subprocess.run(cmd, check=True)
    print(f"已生成：{KEYSTORE}")


def write_signing_properties(alias: str, password: str) -> None:
    target = ROOT / "signing.properties"
    target.write_text(
        "# 本地签名参数（不入库）；CI 由 GitHub Secrets 注入同名环境变量\n"
        f"SESAME_STORE_FILE=keystore/{KEYSTORE.name}\n"
        f"SESAME_STORE_PASSWORD={password}\n"
        f"SESAME_KEY_ALIAS={alias}\n"
        f"SESAME_KEY_PASSWORD={password}\n",
        encoding="utf-8",
    )
    print(f"已写入本地签名参数：{target}")


def print_secrets(alias: str, password: str) -> None:
    data = base64.b64encode(KEYSTORE.read_bytes()).decode("ascii")
    print()
    print("===== 复制到 GitHub → Settings → Secrets and variables → Actions =====")
    print("SESAME_KEYSTORE_BASE64 =")
    print(data)
    print(f"SESAME_STORE_PASSWORD  = {password}")
    print(f"SESAME_KEY_ALIAS       = {alias}")
    print(f"SESAME_KEY_PASSWORD    = {password}")
    print("=====================================================================")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--alias", default=DEFAULT_ALIAS)
    parser.add_argument("--password", default=DEFAULT_PASSWORD)
    parser.add_argument("--print-secrets", action="store_true", help="只打印已有密钥库的 base64")
    args = parser.parse_args()

    if args.print_secrets:
        if not KEYSTORE.exists():
            print(f"密钥库不存在：{KEYSTORE}", file=sys.stderr)
            return 1
        print_secrets(args.alias, args.password)
        return 0

    create_keystore(args.alias, args.password)
    write_signing_properties(args.alias, args.password)
    print_secrets(args.alias, args.password)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
