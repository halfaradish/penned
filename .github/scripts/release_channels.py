#!/usr/bin/env python3
"""发布通道判定（D114）。

用法：
    python3 .github/scripts/release_channels.py plan \
        --name 0.2.0-beta.1 --code 7 \
        --stable /tmp/update.json --prerelease /tmp/update-prerelease.json \
        > /tmp/plan.env
    python3 .github/scripts/release_channels.py --self-test

`plan` 只往 stdout 打 shell env 行（CI 里 `source` 它）：

    CHANNEL=prerelease
    PRERELEASE=true
    WRITE_STABLE=false
    WRITE_PRERELEASE=true
    MIN_VERSION_CODE=1

规则：
- 每次发版 `versionCode` 必须大于两份清单的最大值（Android 不允许降级安装）。
- 正式版：写 update.json；预发布线不倒退时同时覆盖 update-prerelease.json。
- 预发布：只写 update-prerelease.json；base 版本不允许低于当前预发布线。
- 旧线 hotfix（正式版 base 低于预发布线）：只写正式清单，beta 用户留在更高版本线，
  防止语义降级与 Room 数据库降级。
"""

from __future__ import annotations

import argparse
import json
import re
import sys

VERSION_RE = re.compile(r"^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z]+(?:\.[0-9A-Za-z]+)*))?$")


class PlanError(Exception):
    """可读的发布门失败（CI 打 ::error:: 后退出 1）。"""


def parse_version(name: str) -> tuple[str, tuple[int, int, int]]:
    match = VERSION_RE.fullmatch((name or "").strip())
    if not match:
        raise PlanError(f"版本名不合法：{name!r}（正式版写 x.y.z；预发布写 x.y.z-beta.1）")
    base = tuple(int(match.group(i)) for i in range(1, 4))
    return ("prerelease" if match.group(4) else "stable"), base


def base_of(version_name: object) -> tuple[int, int, int]:
    match = VERSION_RE.fullmatch(str(version_name or "").strip())
    if not match:
        return (0, 0, 0)
    return tuple(int(match.group(i)) for i in range(1, 4))


def manifest_info(path: str, label: str, required: bool) -> dict:
    try:
        with open(path, encoding="utf-8") as handle:
            data = json.load(handle)
    except FileNotFoundError:
        if required:
            raise PlanError(f"缺{label}：{path}")
        return {"code": 0, "base": (0, 0, 0), "name": "0.0.0", "min": 1}
    except (OSError, json.JSONDecodeError) as error:
        raise PlanError(f"{label}读不了 / 不是合法 JSON：{path}（{error}）")

    try:
        code = int(data.get("versionCode"))
    except (TypeError, ValueError):
        raise PlanError(f"{label}的 versionCode 不是整数：{data.get('versionCode')!r}")
    if code < 0:
        raise PlanError(f"{label}的 versionCode 不能为负：{code}")

    try:
        minimum = max(1, int(data.get("minVersionCode", 1)))
    except (TypeError, ValueError):
        minimum = 1

    name = str(data.get("versionName", "0.0.0"))
    return {"code": code, "base": base_of(name), "name": name, "min": minimum}


def plan(name: str, code: int, stable: dict, beta: dict) -> dict:
    channel, base = parse_version(name)
    code = int(code)

    if code <= max(stable["code"], beta["code"]):
        raise PlanError(
            f"versionCode 没递增：本次 {code}，正式清单 {stable['code']}，预发布清单 {beta['code']}；"
            "先 ./gradlew bumpVersion 再打 tag"
        )

    if channel == "prerelease":
        if base < beta["base"]:
            raise PlanError(
                f"预发布回旧线：{name} 的 base 低于预发布清单 {beta['name']}"
                "（会降级安装 / Room 数据库降级）"
            )
        return {
            "channel": channel,
            "prerelease": True,
            "write_stable": False,
            "write_prerelease": True,
            "min": beta["min"] if beta["code"] > 0 else 1,
            "warning": None,
        }

    if base < stable["base"]:
        raise PlanError(f"正式版回旧线：{name} 的 base 低于正式清单 {stable['name']}")
    write_beta = base >= beta["base"]
    warning = None
    if not write_beta:
        warning = (
            f"正式版 {name} 的 base 低于预发布线 {beta['name']}：只更新正式清单，"
            "预发布用户留在更高版本线（防语义降级 / DB 降级）"
        )
    return {
        "channel": channel,
        "prerelease": False,
        "write_stable": True,
        "write_prerelease": write_beta,
        "min": stable["min"],
        "warning": warning,
    }


def emit(result: dict) -> None:
    print(f"CHANNEL={result['channel']}")
    print(f"PRERELEASE={'true' if result['prerelease'] else 'false'}")
    print(f"WRITE_STABLE={'true' if result['write_stable'] else 'false'}")
    print(f"WRITE_PRERELEASE={'true' if result['write_prerelease'] else 'false'}")
    print(f"MIN_VERSION_CODE={result['min']}")


def cmd_plan(args: argparse.Namespace) -> int:
    try:
        stable = manifest_info(args.stable, "正式清单", required=True)
        beta = manifest_info(args.prerelease, "预发布清单", required=False)
        result = plan(args.name, args.code, stable, beta)
    except PlanError as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1

    if result["warning"]:
        print(f"::warning::{result['warning']}", file=sys.stderr)
    emit(result)
    return 0


def self_test() -> None:
    stable_010 = {"code": 1, "base": (0, 1, 0), "name": "0.1.0", "min": 1}
    none = {"code": 0, "base": (0, 0, 0), "name": "0.0.0", "min": 1}
    beta_020 = {"code": 2, "base": (0, 2, 0), "name": "0.2.0-beta", "min": 1}
    stable_020 = {"code": 4, "base": (0, 2, 0), "name": "0.2.0", "min": 1}
    beta_030 = {"code": 5, "base": (0, 3, 0), "name": "0.3.0-beta", "min": 1}

    cases = [
        (
            "首个 beta",
            plan("0.2.0-beta", 2, stable_010, none),
            {"channel": "prerelease", "prerelease": True, "write_stable": False,
             "write_prerelease": True, "min": 1},
        ),
        (
            "正式版写两份",
            plan("0.2.0", 5, stable_010, beta_020),
            {"channel": "stable", "prerelease": False, "write_stable": True,
             "write_prerelease": True, "min": 1},
        ),
        (
            "beta.1 续发",
            plan("0.2.0-beta.1", 3, stable_010, beta_020),
            {"channel": "prerelease", "prerelease": True, "write_stable": False,
             "write_prerelease": True, "min": 1},
        ),
        (
            "旧线 hotfix 不碰 beta",
            plan("0.2.1", 6, stable_020, beta_030),
            {"channel": "stable", "prerelease": False, "write_stable": True,
             "write_prerelease": False, "min": 1},
        ),
        (
            "同 base 的正式版覆盖 beta 清单",
            plan("0.2.0", 6, stable_010, beta_020),
            {"channel": "stable", "prerelease": False, "write_stable": True,
             "write_prerelease": True, "min": 1},
        ),
    ]

    for note, got, want in cases:
        for key, value in want.items():
            assert got[key] == value, f"「{note}」{key}: {got[key]!r} != {value!r}"

    failures = [
        ("漏 bumpVersion", lambda: plan("0.2.0", 2, stable_010, beta_020)),
        ("正式版回旧线", lambda: plan("0.1.5", 9, stable_020, none)),
        ("预发布回旧线", lambda: plan("0.1.5-dev", 9, stable_020, beta_030)),
        ("预发布不能回 0.2 线", lambda: plan("0.2.0-beta", 9, stable_020, beta_030)),
        ("非法版本名", lambda: plan("0.2", 9, stable_020, none)),
    ]
    for note, call in failures:
        try:
            call()
        except PlanError:
            continue
        raise AssertionError(f"「{note}」应当被拒绝")

    print(f"release_channels self-test ok：{len(cases)} 正常 + {len(failures)} 失败")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="发布通道判定（D114）")
    parser.add_argument("--self-test", action="store_true", help="跑内置表驱动用例")
    sub = parser.add_subparsers(dest="command")

    plan_parser = sub.add_parser("plan", help="判定本次发布写哪份清单")
    plan_parser.add_argument("--name", required=True, help="versionName（不带 v）")
    plan_parser.add_argument("--code", required=True, type=int, help="versionCode")
    plan_parser.add_argument("--stable", required=True, help="origin/main 的 update.json")
    plan_parser.add_argument("--prerelease", required=True, help="origin/main 的 update-prerelease.json")
    plan_parser.set_defaults(func=cmd_plan)

    args = parser.parse_args(argv)
    if args.self_test:
        self_test()
        return 0
    if getattr(args, "func", None) is not None:
        return args.func(args)

    parser.print_help()
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
