# Skill: Quality Gate

## 適用条件

- 実装完了判定、受け渡し前検証

## 入力（Definition of Ready）

- 変更内容と期待結果が定義されている

## 実施手順

1. build / test / review / docs の必要項目を判定する
2. 対象ゲートを実行し、PASS/FAIL を記録する
3. FAIL 時は再提出条件を明示する

## 出力（Definition of Done）

- 必要ゲートの判定結果が揃っている
- 合否根拠と次アクションが明確

## 差し戻し条件

- 未検証のまま完了扱い
- FAIL の再提出条件が未定義
