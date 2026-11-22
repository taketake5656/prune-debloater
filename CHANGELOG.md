# Changelog

## 2.0.0-ja.1 — 2026-11-23

Prune 日本語版 (taketake5656/prune-debloater) の最初のリリース。本家 2.0.0 がベース。

**New**
- 日本語表示に対応。UI と Prune 独自ルールの文言を日本語化 (端末の言語が日本語のとき)。英語の文言は本家と同じ。
- ルールの訳文は `tools/rules/ja/` に置き、`tools/build_db.py` が検証して `assets/rules/ja/` に出力する。
- 英語/日本語の文字列リソースとルール訳文の整合性を確認するユニットテスト。

**Changed**
- ソースコードのコメントを日本語化。
- Gradle 9.9.2 / Android Gradle Plugin 9.5.2 / Kotlin 2.4.21 (JDK 25 でビルド可能)。
- README を日本語化 (英語版は README.en.md)。
- リリース APK は fork 用の鍵 (CN=taketake5656) で署名する。本家版の上には上書きインストールできない。

## 2.0.0 — 2026-11-21

Prune becomes a universal Android debloater.

**New**
- Manufacturer-independent architecture: device identity (Build fields and system properties), firmware detection and one integration module per manufacturer, with a generic Android fallback that works on every phone. Unknown manufacturers can never crash Prune.
- Integrations: Samsung (One UI), Google Pixel (stock Android), Xiaomi/Redmi/POCO (HyperOS, MIUI), Huawei (EMUI, HarmonyOS 2–4), Honor (MagicOS), OPPO/OnePlus/realme (ColorOS, OxygenOS, realme UI), Motorola (My UX); detection and firmware names for Sony, Nothing, ASUS, vivo/iQOO (Funtouch OS, OriginOS), Tecno/Infinix/itel (HiOS, XOS), Nokia/HMD, Lenovo, ZTE/nubia, LG.
- Package database split into `core.json` and one rule file per manufacturer (348 curated entries in 9 files), plus the full community manufacturer lists (5,396 entries instead of ~2,000).
- Entries can be limited to Android versions or firmwares, and can describe consequences and recovery.
- Google, Huawei and Honor service frameworks are detected independently of the brand. On a phone without Google services, HMS Core and AppGallery (or Honor's services) are locked.
- Compatibility report: device, firmware, integration, compatibility level, access tier, supported and unsupported operations with firmware-specific caveats, what already succeeded or failed on this device, known limitations. Shareable as text.
- Compatibility levels: fully tested, partially tested, experimental, generic, unsupported. Only the emulator claims "fully tested".
- Duplicate apps: app types installed more than once (browsers, galleries, keyboards, app stores...).
- Installer detection: apps placed by partner preload installers (for example Facebook's or Digital Turbine's) are recognised as preloads.
- New risk level **Unknown** for packages in no database: never pre-selected, Custom only, extra confirmation.
- Region-specific app variants are marked.
- Setup steps for Developer options adapt to the manufacturer, including Xiaomi's "USB debugging (Security settings)" and ColorOS's "Disable permission monitoring".

**Changed**
- Texts are no longer Samsung-specific; the home screen shows the detected brand, model, firmware and compatibility level.
- "Features you use" lists the features of the detected phone's brand only.
- Package labels are cached between scans.

**Upgrade**
- Same signing key as 1.0.0: install over it. History and settings are kept. Feature answers saved by 1.0.0 keep applying; features new in 2.0 count as "used" until you answer.

## 1.0.0 — 2026-11-19

First release: Samsung Galaxy debloater and optimizer with four debloat levels, Shizuku/root/ADB-script support, optimization center, history and restore.
