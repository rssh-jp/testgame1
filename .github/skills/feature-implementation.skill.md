# Skill: Feature Implementation

## 適用条件

- 新機能追加、既存機能拡張、仕様ベースの実装
- 主担当の想定: `#implement-feature`

## 入力（Definition of Ready）

- 対象仕様が `docs/spec/` に存在する
- 受け入れ条件（期待挙動）が明文化されている
- 変更対象レイヤ（Model/System/Screen/Render）が特定済み

## 実施手順

1. 仕様差分の要点を 3 行以内で要約する
2. 変更ファイル候補を列挙し、レイヤ責務の妥当性を確認する
3. 最小単位で実装する（1 ステップごとにビルド可能な状態を維持）
4. 必要なら `core/assets/data/` のデータ定義を更新する
5. 関連テスト追加タスクを `#generate-tests` へ委譲する

## 出力（Definition of Done）

- 仕様と一致した Kotlin 実装が完了
- `./gradlew assembleDebug` が成功
- 仕様に影響する変更は `docs/spec/09-implementation-status.md` に反映
- 後続レビューで重大指摘（🔴）が解消済み

## 差し戻し条件

- 仕様未確定のまま実装している
- レイヤ責務違反（例: 画面クラスに戦闘計算を直書き）
- ビルド失敗または受け入れ条件未達

## ハンドオフ

- 次担当: `#generate-tests` または `#code-review`
- 共有内容: 仕様要約、変更ファイル一覧、既知制約
