<p align="center">
  <img src="docs/icon.png" width="96" alt="Prune icon">
</p>

<h1 align="center">Prune</h1>

<p align="center"><a href="README.md">日本語</a> | <b>English</b></p>

<p align="center">
  <b>Open-source Android debloater and optimizer.</b><br>
  Remove what you don't need, keep what you use, undo anything.
</p>

<p align="center">
  <a href="https://github.com/cyb3rl00m/prune-debloater/releases/latest"><img src="https://img.shields.io/badge/release-v2.0.0-3DDC97" alt="Release"></a>
  <img src="https://img.shields.io/badge/Android-9%2B-7C6CFF" alt="Android 9+">
  <img src="https://img.shields.io/badge/root-not%20required-0B0F17" alt="No root required">
  <img src="https://img.shields.io/badge/license-GPL--3.0-blue" alt="GPL-3.0">
  <img src="https://img.shields.io/badge/Made%20with-Claude-D97757" alt="Made with Claude">
</p>

> **Prune is evolving!** Originally developed with Samsung Galaxy devices in mind, Prune is expanding into a universal Android debloating tool. Version 2.0 detects your phone's manufacturer and firmware automatically and brings dedicated rules for Samsung, Google Pixel, Xiaomi, Redmi, POCO, Huawei, Honor, OPPO, OnePlus, realme and Motorola, plus generic support for every other Android phone. So far only the Android emulator is covered by automated tests: real phones are marked *partially tested*, *experimental* or *generic* until people confirm them on their devices. See [Compatibility](docs/COMPATIBILITY.md).

Prune is the Android counterpart of tools like Chris Titus Tech's WinUtil: it gives you control over the software on your phone. It scans every package, explains what it is, rates the risk of removing it, and lets you debloat at four levels, from "just the obvious junk" to "every package, my choice". Every change is previewed, recorded with the state before it, verified after it, and can be undone.

<p align="center">
  <img src="docs/screenshots/01-home.png" width="200" alt="Home">
  <img src="docs/screenshots/01b-compatibility.png" width="200" alt="Compatibility report">
  <img src="docs/screenshots/13-review-disable.png" width="200" alt="Review">
  <img src="docs/screenshots/07-optimize.png" width="200" alt="Optimize">
</p>
<p align="center"><sub>Screenshots from the Android emulator used in CI.</sub></p>

## Download

Get **`Prune-2.0.0.apk`** from the [latest release](https://github.com/cyb3rl00m/prune-debloater/releases/latest), open it on the phone and allow installing from your browser or file manager. The SHA-256 checksum is in `SHA256SUMS.txt` next to it.

**Upgrading from 1.x:** install 2.0.0 over it. It is signed with the same key, so history, settings and your "features you use" answers are kept.

## Supported manufacturers

Prune adapts to the detected device automatically.

| Manufacturer | Firmware | Support level |
|---|---|---|
| Samsung Galaxy | One UI | Partially tested |
| Google Pixel | Stock Android | Partially tested |
| Xiaomi, Redmi, POCO | HyperOS, MIUI | Experimental |
| Huawei | EMUI, HarmonyOS 2–4 | Experimental |
| Honor | MagicOS | Experimental |
| OPPO, OnePlus, realme | ColorOS, OxygenOS, realme UI | Experimental |
| Motorola | My UX | Experimental |
| Sony, Nothing, ASUS, vivo/iQOO, Tecno/Infinix/itel, Nokia/HMD, Lenovo, ZTE/nubia, LG | | Generic |
| Any other Android 9+ phone | | Generic |

- **Partially tested:** dedicated rules; the debloat engine is tested on stock Android, but not on this brand's hardware.
- **Experimental:** dedicated rules and known firmware limitations, tested only with simulated devices.
- **Generic:** generic Android rules plus the community database.

Details, per-brand limitations and how to help move a brand up: [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

**Requirements:** Android 9 (API 28) or newer. HarmonyOS NEXT cannot run Android apps, so Prune cannot run there.

## What Prune does

**Debloat levels**

| Level | For | What it selects |
|---|---|---|
| 1 Minimal | A cleaner phone without real changes | Promoted apps, ad and analytics services, carrier apps, third-party preloads (Facebook, Netflix, ...), optional manufacturer extras. Manufacturer ecosystem features and Google services untouched. |
| 2 Balanced | A noticeably lighter phone | Level 1, plus diagnostics and data-collection services, optional manufacturer apps, optionally Google apps, and background restriction for apps unused for 30 days. |
| 3 Aggressive | As light as reasonably possible | Level 2, plus more manufacturer components and Google apps by default. Read the warnings. |
| 4 Extreme / Custom | Experienced users | Every changeable package with its risk. Starts from the Level 3 selection; high-risk and unknown packages need an extra confirmation. |

Before anything is selected you tell Prune which features you use. The list adapts to the phone: Samsung Wallet, Galaxy Watch, DeX and Bixby on a Galaxy; Xiaomi Cloud, Xiaomi Share and Mi Pay on Xiaomi; Huawei ID and Huawei Share on Huawei; and so on. Packages behind those features are never selected. Apps you opened in the last 14 days are never pre-selected either.

**Package manager.** Every package with its name, package id, description, manufacturer, category (manufacturer, Google, carrier, preload, Android core, other system, yours), installer, status, risk, recommended action, possible consequences and how to bring it back. Search, filter (including duplicates), and act on single packages.

**Package identification.** Prune combines package names, labels, application flags, the installing app (so partner preloads installed during setup are recognised), the manufacturer, the Android version, Prune's own rule files and the community database. Packages Prune knows nothing about are marked **Unknown**, never "safe".

**Duplicate apps.** Two browsers, two galleries, three keyboards: Prune lists app types installed more than once, using Android's standard app categories.

**Compatibility report.** Manufacturer, model, Android version, firmware, Google/Huawei services, available methods, supported and unsupported operations, required permissions, Shizuku and root status, known limitations, and what has already succeeded or failed on this device. Shareable as text.

**Optimization center.** Only changes with a real technical effect, each explained in the app: animation scales, background restriction for unused apps, battery-optimization exemptions, sensitive-permission and notification audits, storage per app and cache trimming (free space measured), screen time and Android's own battery estimates, and app compilation. Prune does not kill processes, change CPU/GPU governors, or touch thermal management.

**Restore center.** Full history with the state before and after each change, undo per change or per batch, "restore original state", a list of every system package currently disabled or removed (by any tool), and an emergency ADB restore script.

## Permission levels

Android does not let a normal app disable or remove system packages. Prune is built around that, not against it:

| Tier | How you get it | What works |
|---|---|---|
| Standard | Install the app | Scanning, classification, explanations, duplicates, compatibility report, usage and storage (with usage access), opening app settings, uninstalling apps you installed |
| ADB level | [Shizuku](https://shizuku.rikka.app/) via wireless debugging (no computer, no root) | Disable, uninstall for user, restore, background restriction, permissions, notifications, battery exemptions, animations, cache trimming, compilation, battery estimates |
| Root | Magisk / KernelSU / APatch (optional) | The same operations through `su`. Prune never needs root. |

No Shizuku and no root? Every plan can be exported as an ADB script and run from a computer. Prune records it and checks afterwards which changes are in effect.

## Set up Shizuku (one time, about 3 minutes)

Prune shows these steps adapted to your phone under **Home → Set up**.

1. Install Shizuku from Google Play or [GitHub](https://github.com/RikkaApps/Shizuku/releases).
2. Turn on Developer options: **Settings → About phone →** tap **Build number** 7 times. (Galaxy: *Software information → Build number*. Xiaomi: *OS version* or *MIUI version*. OPPO/OnePlus/realme: *About device → Version → Build number*.)
3. Connect to Wi-Fi. In **Developer options** turn on **Wireless debugging**.
4. Open Shizuku → **Start via Wireless debugging → Pairing**. In Wireless debugging tap **Pair device with pairing code** and type the code into Shizuku's notification.
5. Tap **Start** in Shizuku, then in Prune: **Home → Set up → Authorize Prune in Shizuku**.
6. Optional: **Grant both** on the same screen gives Prune usage access and the animation permission.

Extra switch on some firmwares:

- **Xiaomi, Redmi, POCO:** also turn on *USB debugging (Security settings)* (needs a SIM card and a Xiaomi account). Without it permission, background and animation changes are refused.
- **OPPO, OnePlus, realme:** turn on *Disable permission monitoring*. Without it permission and background changes are refused.

After a reboot open Shizuku and tap **Start** again.

## Safety model

- **Locked packages.** Telephony and emergency calling, IMS (VoLTE/VoWiFi), System UI, Settings, the shell, permission controllers, package installers, content providers, the network stack, Bluetooth, NFC and the secure element, biometrics, Google Play Services and WebView can never be selected, not even in Custom mode. Each manufacturer file adds its own critical apps: for example One UI Home, Knox Guard and Knox attestation on Samsung; the Security app, permission manager and updater on Xiaomi; Phone Manager and the updater on Huawei; Security Center and the permission manager on OPPO/OnePlus/realme.
- **Locked for this phone.** Your current launcher, phone app, SMS app and keyboard, apps with an accessibility service you turned on, device administrators, persistent system apps, and the service framework and app store a phone without Google services depends on (HMS Core and AppGallery on Huawei).
- **Prune's rules win.** The community database marks for example Find My Mobile, Auto Blocker and Samsung Pay as "Recommended" to remove. Prune's own rules override that.
- **Unknown means unknown.** Packages that are in no database get the risk *Unknown*: never pre-selected, Custom mode only, extra confirmation.
- **Reversible by default.** "Disable" and "Uninstall for this user" both keep the original APK in the system partition. `pm install-existing` brings any package back, and a factory reset restores everything.
- **Verified.** After every command Prune reads the real state back from Android. A change only counts as applied if Android confirms it. Operations a firmware refuses are reported as failed, with the firmware's own error.
- **Commands are argv lists**, never shell strings; package names, app-ops, permissions and settings are validated against strict patterns or whitelists.

## Troubleshooting

| Problem | What to do |
|---|---|
| "Shell cannot change component state" | The firmware refuses to disable that package. Keep "Fallback to uninstall-for-user" on (Settings), or uninstall it for the user from the package screen. |
| Permission or background changes fail on Xiaomi | Turn on *USB debugging (Security settings)* in Developer options. |
| Permission or background changes fail on OPPO/OnePlus/realme | Turn on *Disable permission monitoring* in Developer options. |
| An app came back after a system update | Some firmwares restore system apps during updates. Run your profile again. |
| Prune says "Limited access" after a reboot | Shizuku stops on reboot: open Shizuku and tap Start. |
| The phone misbehaves after a change | **Restore → History → Undo**. If the phone cannot be used normally, connect it to a computer and run the emergency restore script, or `adb shell pm install-existing --user 0 <package>` and `adb shell pm enable --user 0 <package>`. |

## Known limitations

- Manufacturer rule files are curated by hand and cover the best-known packages; the rest relies on the community database or is marked Unknown.
- Package names can change between firmware versions and regions. Prune never assumes that apps with similar names are the same.
- Battery estimates come from `dumpsys batterystats`, whose format differs between firmwares; they can be missing.
- Prune works on the current Android user only (not Secure Folder, Second Space or work profiles).

## What has been tested

| Area | How | Status |
|---|---|---|
| Classification, levels, safety rules, every rule file, command building, output parsers | JVM unit tests on every build | Verified |
| Manufacturer detection and firmware parsing for Samsung, Pixel, Xiaomi (HyperOS/MIUI), Redmi, POCO, Huawei (EMUI/HarmonyOS), Honor, OnePlus, OPPO, realme, Motorola, vivo, Tecno and unknown brands | Unit tests with simulated devices (mock properties) | Verified with mocks only, **not** on hardware |
| Real `pm disable-user`, `pm enable`, `pm uninstall -k --user`, `pm install-existing`, app-ops, `deviceidle`, `settings` | Run as the shell user (same uid as Shizuku) on an Android 15 emulator, result read back from Android | Verified on emulator |
| Device identity, rule loading, duplicate detection, compatibility report on a real Android system | Instrumented tests on the emulator | Verified on emulator |
| Full UI flow: every screen, disable a package and undo it through the UI | Compose UI tests with screenshots on the emulator | Verified on emulator |
| Signed release APK installs and launches | Emulator smoke test | Verified on emulator |
| Shizuku user service binding, root through `su`, manufacturer-specific behaviour | Needs real phones | **Needs device testing** |

## Build from source

Requirements: JDK 17, Android SDK 35.

```
git clone https://github.com/cyb3rl00m/prune-debloater.git
cd prune-debloater
./gradlew testDebugUnitTest assembleDebug
```

The package database is built from `tools/rules/*.json` and the community list:

```
python3 tools/build_db.py
```

Adding or improving a manufacturer: see [CONTRIBUTING.md](CONTRIBUTING.md).

## Credits

- Package descriptions and community removal tiers: [Universal Android Debloater Next Generation](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation) (GPL-3.0). Prune bundles its manufacturer, Google, AOSP, carrier and preload lists.
- [Shizuku](https://github.com/RikkaApps/Shizuku) by RikkaApps for ADB-level access without root.
- Built by cyb3rl00m with help from Claude by Anthropic.

## Disclaimer

Prune changes system software. It is designed to be safe and reversible, but you use it at your own risk. Prune is not affiliated with any phone manufacturer or with Google. Brand and product names are trademarks of their owners.

## License

[GPL-3.0-or-later](LICENSE).
