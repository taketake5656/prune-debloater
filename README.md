<p align="center">
  <img src="docs/icon.png" width="96" alt="Prune アイコン">
</p>

<h1 align="center">Prune</h1>

<p align="center"><b>日本語</b> | <a href="README.en.md">English</a></p>

<p align="center">
  <b>オープンソースの Android 不要アプリ整理 (debloat) &amp; 最適化ツール</b><br>
  いらないものは消し、使うものは残し、どんな変更も元に戻せる。
</p>

<p align="center">
  <a href="https://github.com/cyb3rl00m/prune-debloater/releases/latest"><img src="https://img.shields.io/badge/release-v2.0.0-3DDC97" alt="Release"></a>
  <img src="https://img.shields.io/badge/Android-9%2B-7C6CFF" alt="Android 9+">
  <img src="https://img.shields.io/badge/root-not%20required-0B0F17" alt="root 不要">
  <img src="https://img.shields.io/badge/license-GPL--3.0-blue" alt="GPL-3.0">
  <img src="https://img.shields.io/badge/Made%20with-Claude-D97757" alt="Made with Claude">
</p>

> **このリポジトリについて:** [cyb3rl00m/prune-debloater](https://github.com/cyb3rl00m/prune-debloater) (本家) を fork し、**日本語表示に対応**させたものです。端末の言語が日本語ならアプリの画面、パッケージの説明 (Prune 独自ルール分)、警告、セットアップ手順が日本語で表示され、それ以外の言語では本家と同じ英語で表示されます。コミュニティデータベース (UAD-ng) のパッケージ説明は英語のままです。

> **Prune は進化中です!** もともとは Samsung Galaxy 向けに開発されましたが、Prune はあらゆる Android 端末で使える debloat ツールへと広がりつつあります。バージョン 2.0 は端末のメーカーとファームウェアを自動で検出し、Samsung、Google Pixel、Xiaomi、Redmi、POCO、Huawei、Honor、OPPO、OnePlus、realme、Motorola 向けの専用ルールに加え、その他すべての Android 端末向けの汎用サポートを備えています。現時点で自動テストの対象は Android エミュレータだけです。実機はユーザーによる動作確認が得られるまで *一部テスト済み*、*試験的*、*汎用* として扱います。詳しくは [互換性](docs/COMPATIBILITY.md) (英語) を参照してください。

Prune は、Windows における Chris Titus Tech の WinUtil のような存在を Android で目指したツールで、端末のソフトウェアを自分で管理できるようにします。すべてのパッケージをスキャンし、それが何であるかを説明し、削除した場合の危険度を評価したうえで、「明らかに不要なものだけ」から「すべてのパッケージを自分で選ぶ」まで 4 段階のレベルでスリム化できます。どの変更も事前に確認でき、変更前の状態とともに記録され、変更後に検証され、元に戻すことができます。

<p align="center">
  <img src="docs/screenshots/01-home.png" width="200" alt="ホーム">
  <img src="docs/screenshots/01b-compatibility.png" width="200" alt="互換性レポート">
  <img src="docs/screenshots/13-review-disable.png" width="200" alt="変更の確認">
  <img src="docs/screenshots/07-optimize.png" width="200" alt="最適化">
</p>
<p align="center"><sub>CI で使っている Android エミュレータのスクリーンショット (英語表示)。</sub></p>

## ダウンロード

**日本語版 (この fork):** 現在、この fork からのリリースはありません。[ソースからビルド](#ソースからビルド) してください。

**本家の英語版:** [最新リリース](https://github.com/cyb3rl00m/prune-debloater/releases/latest) から **`Prune-2.0.0.apk`** を入手し、端末で開いて、ブラウザまたはファイルマネージャーからのインストールを許可してください。SHA-256 チェックサムは同じ場所の `SHA256SUMS.txt` にあります。

**1.x からのアップグレード:** 2.0.0 を上書きインストールしてください。同じ鍵で署名されているので、履歴、設定、「使っている機能」の回答は引き継がれます。

自分でビルドした APK は本家のリリースとは署名が異なるため、本家版の上に上書きインストールすることはできません (先にアンインストールが必要で、その場合は履歴と設定が失われます)。

## 対応メーカー

Prune は検出した端末に合わせて自動で動作を切り替えます。

| メーカー | ファームウェア | サポートレベル |
|---|---|---|
| Samsung Galaxy | One UI | 一部テスト済み |
| Google Pixel | 素の Android | 一部テスト済み |
| Xiaomi、Redmi、POCO | HyperOS、MIUI | 試験的 |
| Huawei | EMUI、HarmonyOS 2〜4 | 試験的 |
| Honor | MagicOS | 試験的 |
| OPPO、OnePlus、realme | ColorOS、OxygenOS、realme UI | 試験的 |
| Motorola | My UX | 試験的 |
| Sony、Nothing、ASUS、vivo/iQOO、Tecno/Infinix/itel、Nokia/HMD、Lenovo、ZTE/nubia、LG | | 汎用 |
| その他すべての Android 9 以降の端末 | | 汎用 |

- **一部テスト済み:** 専用ルールあり。debloat エンジンは素の Android でテスト済みですが、このブランドの実機ではテストされていません。
- **試験的:** 専用ルールと既知のファームウェアの制限を組み込み済み。テストはシミュレートした端末のみです。
- **汎用:** 汎用の Android ルールとコミュニティデータベースを使います。

詳細、ブランドごとの制限、サポートレベルを上げるための協力方法は [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) (英語) を参照してください。

**動作条件:** Android 9 (API 28) 以降。HarmonyOS NEXT は Android アプリを実行できないため、Prune は動作しません。

## Prune でできること

**スリム化のレベル**

| レベル | 対象 | 選択されるもの |
|---|---|---|
| 1 最小 | 実質的な変更なしで、すっきりした端末にしたい人 | 宣伝アプリ、広告・分析サービス、キャリアアプリ、サードパーティ製プリインストール (Facebook、Netflix など)、メーカーの任意の追加機能。メーカーのエコシステム機能と Google サービスには触れません。 |
| 2 バランス | 目に見えて軽くしたい人 | レベル 1 に加えて、診断・データ収集サービス、メーカーの任意アプリ、任意で Google アプリ、30 日間使っていないアプリのバックグラウンド制限。 |
| 3 積極的 | 無理のない範囲でできるだけ軽くしたい人 | レベル 2 に加えて、メーカーのコンポーネントをさらに多く、Google アプリも既定で対象。警告を必ず読んでください。 |
| 4 エクストリーム / カスタム | 上級者 | 変更可能なすべてのパッケージを危険度付きで表示。レベル 3 の選択から始まり、高リスクと不明なパッケージには追加の確認が必要です。 |

何かを選択する前に、どの機能を使っているかを Prune に伝えます。この一覧は端末に合わせて変わります。Galaxy なら Samsung Wallet、Galaxy Watch、DeX、Bixby、Xiaomi なら Xiaomi Cloud、Xiaomi Share、Mi Pay、Huawei なら HUAWEI ID、Huawei Share といった具合です。これらの機能に関係するパッケージは決して選択されません。過去 14 日以内に開いたアプリも事前選択されません。

**パッケージマネージャー。** すべてのパッケージについて、名前、パッケージ名、説明、メーカー、分類 (メーカー、Google、キャリア、プリインストール、Android コア、その他のシステム、ユーザーがインストール)、インストール元、状態、危険度、推奨操作、起こりうる影響、元に戻す方法を表示します。検索、絞り込み (重複を含む)、個別パッケージへの操作ができます。

**パッケージの識別。** Prune はパッケージ名、ラベル、アプリケーションフラグ、インストール元のアプリ (初期設定時に入れられたパートナーのプリインストールも識別)、メーカー、Android のバージョン、Prune 独自のルールファイル、コミュニティデータベースを組み合わせて判断します。Prune が何も知らないパッケージは **不明** と表示し、決して「安全」とはしません。

**重複アプリ。** ブラウザが 2 つ、ギャラリーが 2 つ、キーボードが 3 つ。Android 標準のアプリカテゴリを使って、複数インストールされているアプリの種類を一覧にします。

**互換性レポート。** メーカー、モデル、Android のバージョン、ファームウェア、Google/Huawei のサービス、使える方法、対応・非対応の操作、必要な権限、Shizuku と root の状態、既知の制限、この端末ですでに成功・失敗した操作を表示します。テキストとして共有できます。

**最適化センター。** 実際に技術的な効果がある変更だけを、アプリ内で説明付きで提供します: アニメーション倍率、未使用アプリのバックグラウンド制限、電池の最適化の除外、機密性の高い権限と通知の見直し、アプリごとのストレージとキャッシュの削減 (空いた容量を実測)、画面時間と Android 自身による推定電池使用量、アプリのコンパイル。Prune はプロセスの強制終了、CPU/GPU ガバナーの変更、熱管理への干渉は行いません。

**復元センター。** 各変更の前後の状態を含む完全な履歴、変更ごと・まとめての取り消し、「元の状態に戻す」、現在無効化・削除されているすべてのシステムパッケージの一覧 (どのツールによるものも含む)、緊急用の ADB 復元スクリプト。

## 権限レベル

Android では、通常のアプリはシステムパッケージを無効化・削除できません。Prune はこの仕組みに逆らわず、それを前提に作られています。

| 段階 | 入手方法 | できること |
|---|---|---|
| 標準 | アプリをインストールするだけ | スキャン、分類、説明、重複、互換性レポート、使用状況とストレージ (使用状況へのアクセスが必要)、アプリ設定を開く、自分で入れたアプリのアンインストール |
| ADB レベル | ワイヤレスデバッグ経由の [Shizuku](https://shizuku.rikka.app/) (パソコン不要、root 不要) | 無効化、ユーザーからのアンインストール、復元、バックグラウンド制限、権限、通知、電池の最適化の除外、アニメーション、キャッシュ削減、コンパイル、推定電池使用量 |
| root | Magisk / KernelSU / APatch (任意) | 同じ操作を `su` 経由で実行。Prune が root を必要とすることはありません。 |

Shizuku も root もない場合は? どのプランも ADB スクリプトとして書き出し、パソコンから実行できます。Prune はそれを記録し、後でどの変更が反映されたかを確認します。

## Shizuku の設定 (初回のみ、約 3 分)

Prune の **ホーム → 設定する** に、端末に合わせた手順が表示されます。

1. Google Play または [GitHub](https://github.com/RikkaApps/Shizuku/releases) から Shizuku をインストールします。
2. 開発者向けオプションをオンにします: **設定 → デバイス情報 →** **ビルド番号** を 7 回タップします。(Galaxy: *端末情報 → ソフトウェア情報 → ビルド番号*。Xiaomi: *OS バージョン* または *MIUI バージョン*。OPPO/OnePlus/realme: *デバイスについて → バージョン → ビルド番号*。)
3. Wi-Fi に接続します。**開発者向けオプション** で **ワイヤレス デバッグ** をオンにします。
4. Shizuku を開き、**ワイヤレスデバッグ経由で起動 → ペアリング** をタップします。ワイヤレス デバッグで **ペア設定コードによるデバイスのペア設定** をタップし、表示されたコードを Shizuku の通知に入力します。
5. Shizuku で **起動** をタップし、Prune で **ホーム → 設定する → Shizuku で Prune を許可する** をタップします。
6. 任意: 同じ画面の **両方を許可** で、Prune に使用状況へのアクセスとアニメーション用の権限を与えられます。

一部のファームウェアでは追加の設定が必要です:

- **Xiaomi、Redmi、POCO:** *USB デバッグ (セキュリティ設定)* もオンにしてください (SIM カードと Xiaomi アカウントが必要)。オフのままだと、権限・バックグラウンド・アニメーションの変更が拒否されます。
- **OPPO、OnePlus、realme:** *権限の監視を無効にする* をオンにしてください。オフのままだと、権限とバックグラウンドの変更が拒否されます。

端末を再起動したら、Shizuku を開いてもう一度 **起動** をタップしてください。

(ボタン名などは端末・アプリのバージョンや言語設定によって多少異なる場合があります。)

## 安全のしくみ

- **ロックされたパッケージ。** 電話機能と緊急通報、IMS (VoLTE/VoWiFi)、システム UI、設定、シェル、権限コントローラー、パッケージインストーラー、コンテンツプロバイダ、ネットワークスタック、Bluetooth、NFC とセキュアエレメント、生体認証、Google Play 開発者サービス、WebView は、カスタムモードでも決して選択できません。メーカーごとのファイルが独自の重要アプリを追加します。たとえば Samsung では One UI ホーム、Knox Guard、Knox 構成証明、Xiaomi ではセキュリティアプリ、権限マネージャー、アップデーター、Huawei ではスマホマネージャーとアップデーター、OPPO/OnePlus/realme ではセキュリティセンターと権限マネージャーです。
- **この端末でロックされるもの。** 現在のランチャー、電話アプリ、SMS アプリ、キーボード、オンにしたユーザー補助サービスを持つアプリ、デバイス管理アプリ、常駐システムアプリ、そして Google サービスのない端末が依存するサービスフレームワークとアプリストア (Huawei の HMS Core と AppGallery)。
- **Prune のルールが優先。** コミュニティデータベースは、たとえば Find My Mobile、自動ブロッカー、Samsung Pay を削除「推奨」としていますが、Prune 独自のルールがそれを上書きします。
- **不明は不明のまま。** どのデータベースにも載っていないパッケージの危険度は *不明* です。事前選択されず、カスタムモードでのみ表示され、追加の確認が必要です。
- **既定で元に戻せる。** 「無効化」と「このユーザーからアンインストール」のどちらも、元の APK はシステム領域に残ります。`pm install-existing` でどのパッケージも元に戻せ、初期化すればすべて元に戻ります。
- **検証済み。** Prune はコマンドを実行するたびに、Android から実際の状態を読み戻します。変更が適用されたとみなすのは Android が確認した場合だけです。ファームウェアが拒否した操作は、ファームウェア自身のエラーとともに失敗として報告します。
- **コマンドは argv リスト** として渡し、シェル文字列は使いません。パッケージ名、app-ops、権限、設定は厳密なパターンまたはホワイトリストで検証します。

## トラブルシューティング

| 問題 | 対処 |
|---|---|
| 「Shell cannot change component state」と表示される | ファームウェアがそのパッケージの無効化を拒否しています。設定の「ユーザーからのアンインストールで代替」をオンのままにするか、パッケージ画面からこのユーザーからアンインストールしてください。 |
| Xiaomi で権限やバックグラウンドの変更が失敗する | 開発者向けオプションで *USB デバッグ (セキュリティ設定)* をオンにしてください。 |
| OPPO/OnePlus/realme で権限やバックグラウンドの変更が失敗する | 開発者向けオプションで *権限の監視を無効にする* をオンにしてください。 |
| システムアップデート後にアプリが戻ってきた | ファームウェアによってはアップデート時にシステムアプリを復元します。プロファイルをもう一度実行してください。 |
| 再起動後に「アクセス制限あり」と表示される | Shizuku は再起動で停止します。Shizuku を開いて「起動」をタップしてください。 |
| 変更後に端末の動作がおかしい | **復元 → 履歴 → 取り消し** を開いてください。端末を通常どおり使えない場合は、パソコンに接続して緊急復元スクリプトを実行するか、`adb shell pm install-existing --user 0 <パッケージ名>` と `adb shell pm enable --user 0 <パッケージ名>` を実行してください。 |

## 既知の制限

- メーカー別のルールファイルは手作業で管理しており、よく知られたパッケージだけを対象にしています。それ以外はコミュニティデータベースに頼るか、不明として表示されます。
- パッケージ名はファームウェアのバージョンや地域によって変わることがあります。Prune は名前が似ているだけのアプリを同じものとはみなしません。
- 推定電池使用量は `dumpsys batterystats` から取得しますが、その形式はファームウェアによって異なるため、表示されないことがあります。
- Prune が対象とするのは現在の Android ユーザーだけです (セキュリティフォルダ、セカンドスペース、仕事用プロファイルは対象外)。
- 日本語訳があるのはアプリの画面と Prune 独自ルールの説明だけです。コミュニティデータベース由来のパッケージ説明は英語で表示されます。

## テスト状況

| 対象 | 方法 | 状況 |
|---|---|---|
| 分類、レベル、安全規則、全ルールファイル、コマンドの組み立て、出力パーサ、英語/日本語の文字列リソースとルール訳文の整合性 | ビルドごとの JVM ユニットテスト | 検証済み |
| Samsung、Pixel、Xiaomi (HyperOS/MIUI)、Redmi、POCO、Huawei (EMUI/HarmonyOS)、Honor、OnePlus、OPPO、realme、Motorola、vivo、Tecno、不明なブランドのメーカー検出とファームウェアの解析 | シミュレートした端末 (モックのプロパティ) によるユニットテスト | モックでのみ検証済み。実機では **未検証** |
| 実際の `pm disable-user`、`pm enable`、`pm uninstall -k --user`、`pm install-existing`、app-ops、`deviceidle`、`settings` | Android 15 エミュレータ上で shell ユーザー (Shizuku と同じ uid) として実行し、結果を Android から読み戻す | エミュレータで検証済み |
| 実際の Android システム上での端末識別、ルールの読み込み、重複検出、互換性レポート | エミュレータ上の instrumented テスト | エミュレータで検証済み |
| UI の全体の流れ: 全画面の表示、UI からパッケージを無効化して取り消す | エミュレータ上でスクリーンショット付きの Compose UI テスト | エミュレータで検証済み |
| 署名済みリリース APK のインストールと起動 | エミュレータでのスモークテスト | エミュレータで検証済み |
| Shizuku のユーザーサービスへのバインド、`su` 経由の root、メーカー固有の挙動 | 実機が必要 | **実機での検証が必要** |

## ソースからビルド

必要なもの: JDK 17 以上 (JDK 25 まで動作確認済み)、Android SDK 35。

```
git clone https://github.com/taketake5656/prune-debloater.git
cd prune-debloater
./gradlew testDebugUnitTest assembleDebug
```

Windows では `./gradlew` の代わりに `gradlew.bat` を使ってください。デバッグ版の APK は `app/build/outputs/apk/debug/app-debug.apk` に出力されます。

パッケージデータベースは `tools/rules/*.json` (日本語訳は `tools/rules/ja/*.json`) とコミュニティのリストから生成します:

```
python3 tools/build_db.py
```

メーカーの追加や改善については [CONTRIBUTING.md](CONTRIBUTING.md) (英語) を参照してください。

## クレジット

- パッケージの説明とコミュニティの削除段階: [Universal Android Debloater Next Generation](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation) (GPL-3.0)。Prune はそのメーカー、Google、AOSP、キャリア、プリインストールのリストを同梱しています。
- root なしで ADB レベルのアクセスを提供する RikkaApps の [Shizuku](https://github.com/RikkaApps/Shizuku)。
- 開発: cyb3rl00m (Anthropic の Claude の協力のもと)。日本語対応: この fork。

## 免責事項

Prune はシステムソフトウェアを変更します。安全で元に戻せるように設計されていますが、ご利用は自己責任でお願いします。Prune はどの端末メーカーとも Google とも提携していません。ブランド名と製品名は各所有者の商標です。

## ライセンス

[GPL-3.0-or-later](LICENSE)。
