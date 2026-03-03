# Google Play 公開チェックリスト — Tactics Flame

最終更新: 2026年3月2日

## 概要

| 項目 | 値 |
|------|-----|
| App ID | `com.tacticsflame` |
| バージョン | 1 (0.1.0) |
| AAB ファイル | `android/build/outputs/bundle/release/android-release.aab` |
| キーストア | `android/tactics-flame-release.jks` |
| 公開形態（初回） | 内部テスト |

---

## 🔑 キーストア情報（厳重に保管すること）

| 項目 | 値 |
|------|-----|
| ファイル | `android/tactics-flame-release.jks` |
| エイリアス | `tactics-flame` |
| パスワード | `keystore.properties` を参照 |
| 有効期限 | 約27年（10,000日） |

> ⚠️ **重要**: `tactics-flame-release.jks` と `keystore.properties` は絶対にGitにコミットしないこと。
> キーストアを紛失するとアプリの更新が永久に不可能になります。**必ず安全な場所に複数バックアップを取ること。**

---

## ✅ ローカル準備（完了済み）

- [x] 署名用キーストア生成（PKCS12 / RSA 2048bit）
- [x] キーストア設定 (`keystore.properties`)
- [x] ProGuard ルール (`android/proguard-rules.pro`)
- [x] `build.gradle.kts` 署名設定（signingConfigs.release）
- [x] `.gitignore` にキーストア関連ファイルを追加
- [x] `AndroidManifest.xml` 修正（configChanges をactivityに移動、allowBackup=false）
- [x] リリース AAB ビルド成功（9.51 MB）

---

## 📋 Google Play Console での作業手順

### STEP 1: デベロッパーアカウント登録
- [ ] https://play.google.com/console にアクセス
- [ ] Google アカウントでログイン
- [ ] デベロッパー登録料 $25（一度のみ）を支払い
- [ ] 個人/組織情報を入力して登録完了

### STEP 2: アプリを新規作成
- [ ] 「アプリを作成」をクリック
- [ ] アプリ名: `Tactics Flame`
- [ ] デフォルト言語: 日本語
- [ ] アプリまたはゲーム: **ゲーム**
- [ ] 無料または有料: **無料**（後から変更不可）

### STEP 3: ストア掲載情報の設定

#### 必須の素材
| 素材 | サイズ | 備考 |
|------|--------|------|
| アプリアイコン | 512×512 px PNG | 透過なし |
| フィーチャーグラフィック | 1024×500 px PNG/JPG | 必須 |
| スクリーンショット（スマホ） | 最大 8枚 | 最小解像度 320px、アスペクト比 16:9 or 9:16 推奨 |

#### テキスト情報
- [ ] 簡単な説明（80文字以内）
  ```
  炎のタクティカルRPG。SRPGファン必見の戦術バトルゲーム。
  ```
- [ ] 詳細な説明（4000文字以内）
- [ ] アプリのカテゴリ: ロールプレイング
- [ ] タグ: RPG, 戦略, タクティカル
- [ ] 連絡先メールアドレス

### STEP 4: コンテンツのレーティング
- [ ] 「アプリのコンテンツ」→「コンテンツのレーティング」
- [ ] アンケートに回答（暴力、性的コンテンツ等）
- [ ] レーティングが自動付与される（おそらく全年齢対象 or 3+）

### STEP 5: プライバシーポリシー
- [ ] プライバシーポリシーページを作成して公開（URLが必要）
  - 「個人情報を収集しない」旨のシンプルな内容でOK
  - 無料ツール: https://app-privacy-policy-generator.nisrulz.com/
- [ ] Play Console にURLを入力

### STEP 6: アプリのコンテンツ設定
- [ ] ターゲット層: 全年齢（または指定）
- [ ] 広告を含まない: はい（現状広告なし）

### STEP 7: 内部テストのリリース設定
- [ ] 「テスト」→「内部テスト」を選択
- [ ] 「新しいリリースを作成」
- [ ] AABファイルをアップロード:
  ```
  android/build/outputs/bundle/release/android-release.aab
  ```
- [ ] Google Play による App Signing への同意（推奨）
- [ ] リリースノートを入力（例: `初回内部テスト版`）
- [ ] 「保存」→「レビューのために送信」

### STEP 8: テスターの追加
- [ ] 「テスト」→「内部テスト」→「テスター」タブ
- [ ] テスターのGoogleアカウントメールを追加
- [ ] テスターへ招待リンクを共有

---

## 🔄 次バージョンのリリース手順

バージョンを上げる際は以下を更新してから `bundleRelease` を実行:

```kotlin
// android/build.gradle.kts
versionCode = 2        // 毎回インクリメント必須（整数、戻せない）
versionName = "0.2.0"  // 表示用バージョン
```

ビルドコマンド:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :android:bundleRelease
```

---

## ⚠️ 注意事項

1. **versionCode は必ず増やすこと** — 同じ versionCode での再アップロードは不可
2. **キーストアのバックアップ** — 意味：Google Play App Signing に移行すれば万一の際も安全
3. **本番公開前に十分なテスト** — 現状は内部テストからスタートを推奨
4. **targetSdk の最新維持** — Google Play は毎年最新 targetSdk を要求する（現在 targetSdk = 36）
