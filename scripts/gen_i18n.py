#!/usr/bin/env python3
"""从 Android / iOS 源码中收集 tr("…") 文案，并根据 i18n/translations.json 生成两端的译文表。

用法：
    python3 scripts/gen_i18n.py          # 生成 I18nTables.kt / I18nTables.swift
    python3 scripts/gen_i18n.py --check  # 仅检查是否有缺失译文（缺失时返回非零）

translations.json 以简体中文原文为 key：
    {"签到成功": {"zh-Hant": "簽到成功", "en": "Signed in"}}
源码里不以字面量出现的 key（例如枚举标题）写在 "_extraKeys" 数组里。
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TRANSLATIONS = ROOT / "i18n" / "translations.json"
SOURCES = [
    (ROOT / "android-app" / "app" / "src" / "main" / "java", "*.kt"),
    (ROOT / "ios-app", "*.swift"),
]
ANDROID_OUT = ROOT / "android-app/app/src/main/java/com/bjmf/sign/android/data/I18nTables.kt"
IOS_OUT = ROOT / "ios-app/Shared/I18nTables.swift"
LITERAL = re.compile(r'\btr\(\s*"((?:[^"\\]|\\.)*)"')


def collect_keys():
    keys = set()
    for base, pattern in SOURCES:
        for path in base.rglob(pattern):
            if path.name.startswith("I18nTables"):
                continue
            for match in LITERAL.finditer(path.read_text(encoding="utf-8")):
                keys.add(match.group(1).replace('\\"', '"').replace("\\\\", "\\"))
    return keys


def escape(text):
    return text.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n").replace("$", "\\$")


def swift_escape(text):
    return text.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")


def main():
    data = json.loads(TRANSLATIONS.read_text(encoding="utf-8"))
    extra = set(data.pop("_extraKeys", []))
    keys = collect_keys() | extra
    missing = sorted(k for k in keys if k not in data or not data[k].get("en") or not data[k].get("zh-Hant"))
    unused = sorted(k for k in data if k not in keys)
    if missing:
        print("缺少译文：")
        for key in missing:
            print("  " + key)
    if unused:
        print("未使用的译文（可删除）：")
        for key in unused:
            print("  " + key)
    if "--check" in sys.argv:
        sys.exit(1 if missing else 0)

    entries = sorted(k for k in keys if k in data)
    kt = [
        "// 由 scripts/gen_i18n.py 根据 i18n/translations.json 生成，请勿手动修改。",
        "package com.bjmf.sign.android.data",
        "",
        "internal object I18nTables {",
    ]
    for lang, name in (("zh-Hant", "hant"), ("en", "en")):
        kt.append(f"    val {name}: Map<String, String> by lazy {{")
        kt.append("        hashMapOf(")
        for key in entries:
            kt.append(f'            "{escape(key)}" to "{escape(data[key][lang])}",')
        kt.append("        )")
        kt.append("    }")
    kt.append("}")
    ANDROID_OUT.write_text("\n".join(kt) + "\n", encoding="utf-8")

    sw = [
        "// 由 scripts/gen_i18n.py 根据 i18n/translations.json 生成，请勿手动修改。",
        "",
        "enum I18nTables {",
    ]
    for lang, name in (("zh-Hant", "hant"), ("en", "en")):
        sw.append(f"    static let {name}: [String: String] = [")
        for key in entries:
            sw.append(f'        "{swift_escape(key)}": "{swift_escape(data[key][lang])}",')
        sw.append("    ]")
    sw.append("}")
    if IOS_OUT.parent.exists():
        IOS_OUT.write_text("\n".join(sw) + "\n", encoding="utf-8")
    print(f"已生成 {len(entries)} 条译文")


if __name__ == "__main__":
    main()
