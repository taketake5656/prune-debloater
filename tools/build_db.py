#!/usr/bin/env python3
"""Prune のパッケージデータベース資産を生成する。

入力
  tools/uad_lists.json   Universal Android Debloater Next Generation のコミュニティデータ (GPL-3.0)
  tools/rules/*.json     Prune 独自の安全ルール: 全端末共通の core.json とメーカーごとのファイル。
                         Prune のルールは常にコミュニティデータより優先される。
  tools/rules/<言語>/*.json
                         ルールファイルの訳文 (例: ja/)。元のファイルと同名で、説明文だけを持つ。
                         キーが元のルールに存在するかを検証する。訳のない項目は英語のまま表示される。

出力 (app/src/main/assets/)
  packages_uad.json      メーカー、Google、AOSP、キャリア、一般的なプリロードのエントリ (短縮キー)
  rules/*.json           検証済みのルールファイルのコピー
  rules/index.json       ルールファイルの一覧 (core が先頭)
  rules/<言語>/*.json    検証済みの訳文のコピー

リポジトリのルートで実行する:  python3 tools/build_db.py
先にコミュニティデータを更新する場合: curl -o tools/uad_lists.json \
  https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
RULES_SRC = ROOT / "tools" / "rules"
PKG_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$|^android$")

LIST_CODE = {"Oem": "o", "Google": "g", "Aosp": "a", "Carrier": "c", "Misc": "m"}
REMOVAL_CODE = {"Recommended": "r", "Advanced": "a", "Expert": "e", "Unsafe": "u"}
RISKS = {"SAFE", "LOW", "MODERATE", "HIGH"}
REQUIRED_KEYS = {"schema", "manufacturer", "updated", "locked", "lockedPrefixes", "ecosystems", "featureTags",
                 "entries", "prefixEntries"}


def fail(msg: str) -> None:
    print("ERROR:", msg, file=sys.stderr)
    sys.exit(1)


def clean_description(text: str) -> str:
    text = text.replace("\r", "").strip()
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text[:1200]


def build_uad() -> dict:
    data = json.loads((ROOT / "tools" / "uad_lists.json").read_text(encoding="utf-8"))
    out = {}
    for pkg, v in data.items():
        if not PKG_RE.match(pkg):
            continue
        lst = v.get("list", "")
        removal = v.get("removal", "")
        if lst not in LIST_CODE or removal not in REMOVAL_CODE:
            continue
        entry = {"l": LIST_CODE[lst], "r": REMOVAL_CODE[removal], "d": clean_description(v.get("description", ""))}
        deps = [d for d in v.get("dependencies", []) if PKG_RE.match(d)]
        needed = [d for d in v.get("neededBy", []) if PKG_RE.match(d)]
        if deps:
            entry["dep"] = deps
        if needed:
            entry["nb"] = needed
        out[pkg] = entry
    return out


def validate_file(name: str, rules: dict) -> None:
    missing = REQUIRED_KEYS - rules.keys()
    if missing:
        fail(f"{name}: missing keys {sorted(missing)}")
    for e in rules["locked"]:
        if not PKG_RE.match(e["id"]) or not e.get("reason"):
            fail(f"{name}: bad locked entry {e}")
    for e in rules["lockedPrefixes"]:
        if not e["prefix"] or not e.get("reason"):
            fail(f"{name}: bad locked prefix {e}")
    for g in rules["ecosystems"] + rules["featureTags"]:
        if not g.get("warning") or not g.get("name") or not g.get("key"):
            fail(f"{name}: group {g.get('key')} needs key, name and warning")
        for p in g.get("packages", []):
            if not PKG_RE.match(p):
                fail(f"{name}: bad package {p} in {g['key']}")
    for pkg, e in rules["entries"].items():
        if not PKG_RE.match(pkg):
            fail(f"{name}: bad entry id {pkg}")
        if e.get("level") not in (1, 2, 3, 4):
            fail(f"{name}: {pkg}: level must be 1-4")
        if e.get("risk") not in RISKS:
            fail(f"{name}: {pkg}: risk must be one of {RISKS}")
        if e["risk"] == "HIGH" and e["level"] != 4:
            fail(f"{name}: {pkg}: HIGH risk packages may only be selected in Custom (level 4)")
        for k in ("minSdk", "maxSdk"):
            if k in e and not isinstance(e[k], int):
                fail(f"{name}: {pkg}: {k} must be an integer")
    for e in rules["prefixEntries"]:
        if e.get("level") not in (1, 2, 3, 4) or e.get("risk") not in RISKS:
            fail(f"{name}: bad prefix entry {e}")


def validate_all(files: dict) -> None:
    locked = {e["id"] for r in files.values() for e in r["locked"]}
    prefixes = [e["prefix"] for r in files.values() for e in r["lockedPrefixes"]]
    seen_entries = {}
    eco_keys = {}
    for name, r in files.items():
        for g in r["ecosystems"]:
            if g["key"] in eco_keys and eco_keys[g["key"]] != name:
                fail(f"ecosystem {g['key']} defined in both {eco_keys[g['key']]} and {name}")
            eco_keys[g["key"]] = name
            for p in g.get("packages", []):
                if p in locked:
                    fail(f"{p} is both locked and in group {g['key']} ({name})")
        for pkg in r["entries"]:
            if pkg in locked:
                fail(f"{pkg} is locked but has a selectable entry in {name}")
            if any(pkg.startswith(p) for p in prefixes):
                fail(f"{pkg} matches a locked prefix but has an entry in {name}")
            if pkg in seen_entries:
                fail(f"{pkg} has entries in both {seen_entries[pkg]} and {name}")
            seen_entries[pkg] = name


TRANSLATION_SECTIONS = {"locked", "lockedPrefixes", "groups", "entries", "prefixEntries"}
ENTRY_TEXT_KEYS = {"note", "consequences", "recovery"}


def validate_translation(lang: str, name: str, t: dict, rules: dict) -> None:
    """訳文のキーが元のルールに存在し、値が空でない文字列であることを確認する。"""
    where = f"{lang}/{name}"
    unknown = set(t) - TRANSLATION_SECTIONS
    if unknown:
        fail(f"{where}: unknown sections {sorted(unknown)}")

    def text(v, what):
        if not isinstance(v, str) or not v.strip():
            fail(f"{where}: {what} must be a non-empty string")

    locked = {e["id"] for e in rules["locked"]}
    for k, v in t.get("locked", {}).items():
        if k not in locked:
            fail(f"{where}: locked {k} is not in {name}")
        text(v, f"locked {k}")
    prefixes = {e["prefix"] for e in rules["lockedPrefixes"]}
    for k, v in t.get("lockedPrefixes", {}).items():
        if k not in prefixes:
            fail(f"{where}: locked prefix {k} is not in {name}")
        text(v, f"locked prefix {k}")
    groups = {g["key"] for g in rules["ecosystems"] + rules["featureTags"]}
    for k, v in t.get("groups", {}).items():
        if k not in groups:
            fail(f"{where}: group {k} is not in {name}")
        if not isinstance(v, dict) or not v or set(v) - {"name", "warning"}:
            fail(f"{where}: group {k} may only have name and warning")
        for f, s in v.items():
            text(s, f"group {k}.{f}")
    for k, v in t.get("entries", {}).items():
        if k not in rules["entries"]:
            fail(f"{where}: entry {k} is not in {name}")
        if not isinstance(v, dict) or not v or set(v) - ENTRY_TEXT_KEYS:
            fail(f"{where}: entry {k} may only have {sorted(ENTRY_TEXT_KEYS)}")
        for f, s in v.items():
            if not rules["entries"][k].get(f):
                fail(f"{where}: entry {k} translates {f}, which {name} does not have")
            text(s, f"entry {k}.{f}")
    prefix_entries = {e["prefix"] for e in rules["prefixEntries"]}
    for k, v in t.get("prefixEntries", {}).items():
        if k not in prefix_entries:
            fail(f"{where}: prefix entry {k} is not in {name}")
        text(v, f"prefix entry {k}")


def load_translations(files: dict) -> dict:
    """{言語: {ファイル名: 訳文}} を読み込んで検証する。"""
    out = {}
    for lang_dir in sorted(p for p in RULES_SRC.iterdir() if p.is_dir()):
        lang = lang_dir.name
        out[lang] = {}
        for p in sorted(lang_dir.glob("*.json")):
            if p.name not in files:
                fail(f"{lang}/{p.name}: there is no tools/rules/{p.name} to translate")
            t = json.loads(p.read_text(encoding="utf-8"))
            validate_translation(lang, p.name, t, files[p.name])
            out[lang][p.name] = t
    return out


def main() -> None:
    names = sorted(p.name for p in RULES_SRC.glob("*.json"))
    if "core.json" not in names:
        fail("tools/rules/core.json is required")
    names.remove("core.json")
    names.insert(0, "core.json")
    files = {}
    for n in names:
        rules = json.loads((RULES_SRC / n).read_text(encoding="utf-8"))
        validate_file(n, rules)
        files[n] = rules
    validate_all(files)
    translations = load_translations(files)
    uad = build_uad()

    out_rules = ASSETS / "rules"
    out_rules.mkdir(parents=True, exist_ok=True)
    for old in out_rules.glob("*.json"):
        old.unlink()
    for n, rules in files.items():
        (out_rules / n).write_text(json.dumps(rules, ensure_ascii=False, indent=1), encoding="utf-8")
    (out_rules / "index.json").write_text(json.dumps({"files": names}, indent=1), encoding="utf-8")
    # 訳文: 古い出力を消してから書き直す (元のルールから消えた言語やファイルを残さないため)。
    for old_dir in (p for p in out_rules.iterdir() if p.is_dir()):
        for old in old_dir.glob("*.json"):
            old.unlink()
        old_dir.rmdir()
    for lang, tr in translations.items():
        (out_rules / lang).mkdir()
        for n, t in tr.items():
            (out_rules / lang / n).write_text(json.dumps(t, ensure_ascii=False, indent=1), encoding="utf-8")
    (ASSETS / "packages_uad.json").write_text(
        json.dumps(uad, ensure_ascii=False, separators=(",", ":"), sort_keys=True), encoding="utf-8")
    legacy = ASSETS / "prune_rules.json"
    if legacy.exists():
        legacy.unlink()
    total = sum(len(r["entries"]) for r in files.values())
    print(f"packages_uad.json: {len(uad)} entries; {len(files)} rule files, {total} curated entries")
    for n, r in files.items():
        print(f"  {n:20} {r['manufacturer']:28} locked {len(r['locked']):3}  entries {len(r['entries']):3}")
    for lang, tr in translations.items():
        print(f"translations {lang}: {', '.join(tr)}")


if __name__ == "__main__":
    main()
