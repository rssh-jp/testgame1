# Agent / Skill / Prompt 対応表

Tactics Flame の運用で使う `agent`・`skill`・`prompt` の対応を 1 ページで確認するための早見表です。

## 対応マトリクス（1 Agent : 多 Skills）

共通Skill:

- `.github/skills/requirement-analysis.skill.md`
- `.github/skills/impact-assessment.skill.md`
- `.github/skills/quality-gate.skill.md`
- `.github/skills/handoff-reporting.skill.md`

| 役割 | Agent | Skills（専用 + 共通） | Prompt |
|---|---|---|---|
| 統括実行 | `.github/agents/orchestrator.agent.md` | `.github/skills/orchestration.skill.md` + 共通4 | `.github/prompts/orchestrator.prompt.md` |
| 仕様設計 | `.github/agents/game-design.agent.md` | `.github/skills/game-design.skill.md` + requirement/impact/handoff | `.github/prompts/game-design.prompt.md` |
| 機能実装 | `.github/agents/implement-feature.agent.md` | `.github/skills/feature-implementation.skill.md` + 共通4 | `.github/prompts/implement-feature.prompt.md` |
| バグ修正 | `.github/agents/debug-gameplay.agent.md` | `.github/skills/bugfix.skill.md` + 共通4 | `.github/prompts/debug-gameplay.prompt.md` |
| バランス調整 | `.github/agents/battle-tuning.agent.md` | `.github/skills/battle-tuning.skill.md` + 共通4 | `.github/prompts/battle-tuning.prompt.md` |
| UI改善 | `.github/agents/ui-enhancement.agent.md` | `.github/skills/ui-enhancement.skill.md` + 共通4 | `.github/prompts/ui-enhancement.prompt.md` |
| マップ作成 | `.github/agents/map-creator.agent.md` | `.github/skills/map-creator.skill.md` + 共通4 | `.github/prompts/map-creator.prompt.md` |
| テスト生成 | `.github/agents/generate-tests.agent.md` | `.github/skills/test-addition.skill.md` + 共通4 | `.github/prompts/generate-tests.prompt.md` |
| コードレビュー | `.github/agents/code-review.agent.md` | `.github/skills/code-review.skill.md` + impact/quality/handoff | `.github/prompts/code-review.prompt.md` |
| リファクタ | `.github/agents/refactor.agent.md` | `.github/skills/refactor.skill.md` + 共通4 | `.github/prompts/refactor.prompt.md` |
| ドキュメント更新 | `.github/agents/generate-docs.agent.md` | `.github/skills/generate-docs.skill.md` + impact/quality/handoff | `.github/prompts/generate-docs.prompt.md` |
| Git運用 | `.github/agents/git-ops.agent.md` | `.github/skills/git-ops.skill.md` + impact/quality/handoff | `.github/prompts/git-ops.prompt.md` |

## 運用ルール

1. 役割を追加する場合は **Agent / Prompt + 専用Skill + 必要な共通Skill参照** を同時に追加する
2. 仕様変更で手順が変わる場合は **先に Skill を更新** し、次に Agent/Prompt を同期する
3. 参照漏れを防ぐため、更新後に以下を確認する
   - 全 `agent` に `参照スキル` または `利用可能なスキル` がある
   - 全 `prompt` に `参照スキル` がある
   - 対応表の行数が Agent 数と一致する

## 更新チェック手順（手動）

1. `.github/agents/` の件数を確認
2. `.github/prompts/` の件数を確認
3. `.github/skills/` の件数を確認（`README.md` 除く）
4. 本ファイルの行数と役割数が一致することを確認
