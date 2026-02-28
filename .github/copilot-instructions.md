# Copilot カスタム指示

## プロジェクト概要
このプロジェクトは **Tactics Flame** — ファイアーエムブレム風のタクティカルRPG（Android向け）です。

## 技術スタック
- **言語**: Kotlin
- **ゲームフレームワーク**: LibGDX 1.12+
- **ビルドツール**: Gradle (Kotlin DSL)
- **対象プラットフォーム**: Android (API 26+) / Desktop（開発用）
- **アーキテクチャ**: Model / System / Screen / Render の分離

## コーディング規約
- 変数名・関数名はキャメルケース（camelCase）を使用する
- クラス名はパスカルケース（PascalCase）を使用する
- コメントは日本語で記述する
- 関数には KDoc スタイルのドキュメントを付ける
- パッケージ構成: `com.tacticsflame.{model|system|screen|render|ui|data|input|util|core}`

## レスポンスルール
- 回答は日本語で行うこと
- コード例を示す際は、簡潔で分かりやすい説明を添えること
- エラーが発生した場合は原因と解決策を具体的に提示すること
- ゲーム開発に関するベストプラクティスを考慮すること

## セキュリティ
- シークレットやAPIキーをコードに直接含めないこと
- 環境変数や設定ファイル(.env)を使用すること
- .gitignore に機密ファイルを含めること
