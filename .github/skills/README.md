# Tactics Flame Skills

このディレクトリは、エージェント間で再利用する実務手順（Skill）を管理します。

関連資料:

- `.github/agent-skill-prompt-map.md`（Agent / Skill / Prompt 対応表）

## 目的

- Agent: 判断・委譲・品質統制
- Skill: 実行手順のテンプレート化（DoR/手順/DoD/差し戻し）

## スキル一覧

- `orchestration.skill.md`
- `game-design.skill.md`
- `feature-implementation.skill.md`
- `bugfix.skill.md`
- `battle-tuning.skill.md`
- `ui-enhancement.skill.md`
- `map-creator.skill.md`
- `test-addition.skill.md`
- `code-review.skill.md`
- `refactor.skill.md`
- `generate-docs.skill.md`
- `git-ops.skill.md`
- `requirement-analysis.skill.md`
- `impact-assessment.skill.md`
- `quality-gate.skill.md`
- `handoff-reporting.skill.md`

## 構成モデル

- 基本は **1 Agent : 多 Skills**
- 推奨は「専用Skill 1つ + 共通Skill 2〜4つ」
- 共通Skill（要件整理 / 影響分析 / 品質ゲート / 受け渡し）を横断利用する

## 運用ルール

1. 同じ手順を2回以上使う見込みがある場合は Skill 化する
2. Skill は「1責任」に限定し、複合タスクは orchestrator 側で連結する
3. 仕様の正本は `docs/spec/` に置き、Skill には要約のみを書く
4. Skill の更新時は、対応する Agent の参照リンクも更新する
