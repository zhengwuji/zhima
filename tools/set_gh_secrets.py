#!/usr/bin/env python3
"""
把本地签名密钥库写入 GitHub 仓库 Secrets（免手抄）。

依赖：pip install pynacl requests
用法：
    python tools/set_gh_secrets.py                 # 读取 keystore/sesame-release.jks + signing.properties
    python tools/set_gh_secrets.py --repo zhengwuji/zhima
环境变量 GITHUB_TOKEN（或 GH_TOKEN）需具备该仓库的 Secrets 写权限（细粒度 PAT: Secrets: Read and write）。

说明：GitHub 要求用仓库公钥对 secret 加密后再上传，本脚本用 PyNaCl 完成这一步。
"""

import argparse
import base64
import json
import os
import pathlib
import sys
import urllib.request

try:
    from nacl import encoding, public
except ImportError:  # pragma: no cover
    print("缺少依赖：pip install pynacl", file=sys.stderr)
    raise SystemExit(2)

ROOT = pathlib.Path(__file__).resolve().parent.parent
KEYSTORE = ROOT / "keystore" / "sesame-release.jks"
SIGNING_PROPS = ROOT / "signing.properties"


def api(method: str, url: str, token: str, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("User-Agent", "sesame-m-secrets")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=60) as resp:
        body = resp.read().decode() or "{}"
        return resp.status, json.loads(body)


def load_signing_props() -> dict:
    props = {}
    if SIGNING_PROPS.exists():
        for line in SIGNING_PROPS.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            props[k.strip()] = v.strip()
    return props


def encrypt(public_key: str, secret_value: str) -> str:
    pk = public.PublicKey(public_key.encode(), encoding.Base64Encoder())
    sealed = public.SealedBox(pk).encrypt(secret_value.encode())
    return base64.b64encode(sealed).decode()


def put_secret(repo: str, token: str, name: str, value: str) -> None:
    status, body = api("GET", f"https://api.github.com/repos/{repo}/actions/secrets/public-key", token)
    if status != 200:
        raise RuntimeError(f"获取仓库公钥失败: {status} {body}")
    encrypted = encrypt(body["key"], value)
    status, _ = api(
        "PUT",
        f"https://api.github.com/repos/{repo}/actions/secrets/{name}",
        token,
        {"encrypted_value": encrypted, "key_id": body["key_id"]},
    )
    print(f"  {'OK ' if status in (201, 204) else 'ERR'} {name}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY", "zhengwuji/zhima"))
    args = parser.parse_args()

    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if not token:
        print("请先设置 GITHUB_TOKEN（细粒度 PAT，需 Secrets: Read and write）", file=sys.stderr)
        return 2
    if not KEYSTORE.exists():
        print(f"密钥库不存在：{KEYSTORE}（先运行 tools/gen_keystore.py）", file=sys.stderr)
        return 2

    props = load_signing_props()
    store_password = props.get("SESAME_STORE_PASSWORD")
    key_alias = props.get("SESAME_KEY_ALIAS", "sesame")
    key_password = props.get("SESAME_KEY_PASSWORD", store_password)
    if not store_password:
        print("signing.properties 缺少 SESAME_STORE_PASSWORD", file=sys.stderr)
        return 2

    print(f"写入 {args.repo} 的 Actions secrets：")
    put_secret(args.repo, token, "SESAME_KEYSTORE_BASE64",
               base64.b64encode(KEYSTORE.read_bytes()).decode("ascii"))
    put_secret(args.repo, token, "SESAME_STORE_PASSWORD", store_password)
    put_secret(args.repo, token, "SESAME_KEY_ALIAS", key_alias)
    put_secret(args.repo, token, "SESAME_KEY_PASSWORD", key_password)
    print("完成。下次 push 到 main 即会构建并发布签名 APK。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
