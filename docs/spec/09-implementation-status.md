# 09. 実装状況

最終更新: 2026年3月9日

## 凡例

| 記号 | 意味 |
|------|------|
| ✅ | 実装済み・動作確認済み |
| ⚠️ | 部分実装（バックエンドのみ等） |
| ❌ | 未実装 |

---

## 1. プロジェクト構成

| 項目 | 状況 | 備考 |
|------|------|------|
| マルチモジュール構成 (core/android/desktop) | ✅ | Gradle Kotlin DSL |
| LibGDX 1.12.1 統合 | ✅ | |
| Android ビルド・APK出力 | ✅ | API 26+ / TargetSDK 36 |
| Desktop ビルド | ✅ | 開発用 |
| エミュレータへのデプロイ | ✅ | emulator-5554 |
| ネイティブライブラリ (.so) 組み込み | ✅ | copyAndroidNatives タスク |
| **画面向き: 縦画面 (Portrait)** | **✅** | **仮想解像度 1080×1920, AndroidManifest portrait固定** |

## 2. データモデル

| 項目 | 状況 | 備考 |
|------|------|------|
| GameUnit クラス | ✅ | 全ステータス・武器・経験値・CT — **ジョブベースステータス（stats = unitClass.baseStats + personalModifier + levelUpStats）** |
| Weapon クラス | ✅ | 武器タイプ・攻撃力・命中・射程・重さ・**healPowerプロパティ追加（isHealingStaff判定）** |
| **Armor クラス** | **✅** | **防具タイプ・DEF/RESボーナス・重さ** |
| **ArmorType enum** | **✅** | **LIGHT_ARMOR/HEAVY_ARMOR/SHIELD/MAGIC_ROBE/ACCESSORY/HEAD/FEET** |
| **装備スロットシステム** | **✅** | **新規: rightHand/leftHand/armorSlot1/armorSlot2 の4スロット** |
| **二刀流システム** | **✅** | **UnitClass.canDualWield/dualWieldPenalty（全10職業で左手装備可能）、副手命中ペナルティ-15** |
| **armors.json (防具マスターデータ)** | **✅** | **新規追加: 10種の防具定義** |
| BattleMap クラス | ✅ | グリッド管理・地形取得 |
| TerrainType enum | ✅ | 8種（PLAIN/FOREST/MOUNTAIN/WATER/FORT/WALL/VILLAGE/BRIDGE）、**hitBonusプロパティ追加** |
| ClassType enum | ✅ | 10クラス定義 |
| Faction enum | ✅ | PLAYER/ENEMY/ALLY |
| Stats data class | ✅ | HP/STR/MAG/SKL/SPD/LCK/DEF/RES — **内部Float・effectiveXxx(Int切捨)方式** |
| CT プロパティ (GameUnit.ct) | ✅ | **新規追加** |
| マップデータ JSON 読み込み | ✅ | MapLoader で chapter_1〜12.json を読み込み |
| ユニットデータ JSON 読み込み | ✅ | units.json 内のテストデータ |
| **チャプターマップ JSON (chapter_1〜12)** | **✅** | **12チャプター分のマップ・敵配置データ（chapter_7〜12を追加）** |
| **ChapterInfo データクラス** | **✅** | **新規追加: チャプター情報（ID・名前・マップファイル・ワールドマップ座標）** |
| **PartyState クラス** | **✅** | **パーティ管理（ロスター・出撃メンバー選択・武器/防具在庫）** |
| **GameProgress クラス** | **✅** | **キャンペーン進行状態（チャプター開放・クリア管理）・**周回システム（cycle/startNewCycle）** |
| **UnitTactic enum** | **✅** | **新規追加: ユニット作戦（CHARGE/CAUTIOUS/SUPPORT/HEAL/FLEE）— 部隊編成で設定** |
| **BattleConfig データクラス** | **✅** | **新規追加: バトル設定（マップ・ユニット配置・勝利条件）** |
| **MapLoader（JSONマップローダー）** | **✅** | **新規追加: JSON→BattleMap/敵/スポーン/勝利条件の一括読み込み** |
| **敵ユニットレベルアップ能力値補正** | **✅** | **バグ修正: parseEnemyUnit/generateRandomEnemies でクラス固有成長率(classGrowthRate)による levelUpStats を適用。generateLevelUpStats()を引数必須化・乗算方式に改善。デッドコード(ENEMY_BASE_STATS/parseStats)削除** |
| **UnitClassLoader baseStats パース** | **✅** | **バグ修正: baseStats未パースにより全クラスHP=0→敵非表示の不具合を修正。parseStats()メソッド追加、classes.json全10クラスにbaseStatsデータ追加、HP=0警告ログ付き妥当性チェック** |
| **BattleResultData データクラス** | **✅** | **新規追加: 戦闘結果（勝敗・ラウンド数・撃破数・生存ユニット）** |
| **GrowthRate data class** | **✅** | **Float固定値加算方式に変更（旧: Int%確率成長）、SPD=0.20f一律** |
| **ジョブベースステータスシステム** | **✅** | **stats = unitClass.baseStats + personalModifier + levelUpStats、転職HP差分調整** |
| **セーブデータ v6** | **✅** | **周回(cycle)対応（旧v5データのcycle=0フォールバック互換あり）** |
| **StatGrowth data class** | **✅** | **新規追加: レベルアップ時のInt実効値変化量（UI表示用）** |
| **GrowthRate 読み込み仕様** | **✅** | **保存値を固定加算値としてそのまま復元（%としての自動変換なし）** |
| **personalGrowthRate 二重加算バグ修正** | **✅** | **バグ修正: setupInitialParty()で personalGrowthRate に仕様10の「合計成長率」が設定されていたため、levelUp()の personalGrowthRate + classGrowthRate 合算で classGrowthRate が二重加算され成長速度が仕様の約1.7〜3.3倍に。全5キャラの personalGrowthRate を「仕様10合計 − classGrowthRate」の正しい値に修正** |
| **成長システム仕様書** | **✅** | **新規追加: docs/spec/10-growth-system.md** |

## 3. バトルシステム

| 項目 | 状況 | 備考 |
|------|------|------|
| ダメージ計算 (DamageCalc) | ✅ | 物理/魔法・地形防御・**防具ボーナス**考慮 |
| 命中率計算 | ✅ | 武器命中 + SKL×2 + LCK + **攻撃側地形命中補正** - 回避（**実効速度ベース**） |
| クリティカル計算 | ✅ | SKL/2 - LCK |
| 追撃判定（攻速差5以上） | ✅ | **実効速度(effectiveSpeed)ベースに変更** |
| 武器三すくみ | ✅ | 命中±15、**素手は三すくみ対象外** |
| 戦闘予測 (BattleForecast) | ✅ | |
| 戦闘実行 (executeBattle) | ✅ | 攻撃→反撃→追撃、**素手攻撃対応** |
| **素手攻撃システム** | **✅** | **新規追加: 武器未装備でも射程1で攻撃可能（might=0, hit=80）** |
| **防具システム** | **✅** | **防具のDEF/RESボーナスがダメージ軽減に反映（2スロット合算）** |
| **装備重量→スピード減少** | **✅** | **effectiveSpeed() = SPD - 右手重さ - 左手重さ - 防具1重さ - 防具2重さ - 二刀流ペナ** |
| **二刀流攻撃** | **✅** | **新規: 右手→反撃→左手→右手追撃→防御側追撃** |
| 経験値計算 | ✅ | レベル差補正あり、GameConfig定数参照 |
| レベルアップ処理 | ✅ | **決定論的成長（Float固定値加算、確率なし）** |
| **経験値付与（戦闘後）** | **✅** | **新規追加: プレイヤーユニットが戦闘後にEXP獲得、レベルアップ対応** |
| **撃破ボーナス経験値** | **✅** | **新規追加: 敵撃破時に+20EXPボーナス** |
| **防御側経験値獲得** | **✅** | **新規追加: 反撃命中時に経験値獲得** |
| **レベル上限** | **✅** | **上限撤廃済み: 際限なくレベルアップ可能** |
| **地形命中補正** | **✅** | **新規追加: 攻撃側地形のhitBonusが命中率に加算（森+10/山+15/砦+10/村+5）** |
| **水域ペナルティ** | **✅** | **新規追加: 水域上のユニットに命中-15・回避-15の負補正** |
| **プレイヤー操作からの戦闘呼び出し** | **❌** | **ACTIONメニュー未実装** |
| **回復システム（杖）** | **✅** | **HealForecast/HealResult、回復量=MAG+healPower、回復EXP=20+実回復量/2、自己回復対応** |
| **回復杖データ** | **✅** | **ライブ(healPower=10)、リカバー(healPower=20)をweapons.jsonに追加** |

## 4. ターン管理（CTベース個別ターン制）

| 項目 | 状況 | 備考 |
|------|------|------|
| TurnManager (CTベース) | ✅ | **旧フェイズ制から全面刷新** |
| CT蓄積（毎ティック CT += SPD） | ✅ | **effectiveSpeed()ベースに変更**、SPD=0でも最侎1加算 |
| CT閾値判定（CT >= 100） | ✅ | GameConfig.CT_THRESHOLD |
| CT超過持ち越し（CT -= 100） | ✅ | 高速ユニット有利 |
| CT行動優先度（CT値→SPD値） | ✅ | タイブレイク実装済み |
| ラウンドカウント | ✅ | 全生存ユニット行動でラウンド+1 |
| 行動順予測（UI表示用） | ✅ | predictActionOrder() |
| ~~PLAYER → ENEMY → ALLY フェイズ~~ | **廃止** | CTベースに移行 |
| ~~Phase enum~~ | **廃止** | 不要 |
| ~~同盟ユニットなし時のALLYスキップ~~ | **廃止** | CTで自動管理 |
| ~~フェイズ開始時の hasActed リセット~~ | **廃止** | CTで管理 |
| ~~全員行動済み判定~~ | **廃止** | CTで管理 |

## 5. 勝敗判定

| 項目 | 状況 | 備考 |
|------|------|------|
| VictoryChecker | ✅ | |
| DEFEAT_ALL | ✅ | 敵全滅で勝利 |
| DEFEAT_BOSS | ✅ | isLord 判定 |
| SURVIVE_TURNS | ✅ | ラウンド数で判定 |
| REACH_POINT | ❌ | 到達点指定なし |
| 敗北条件（ロード死亡） | ✅ | ロードが存在し戦闘不能時のみ敗北 |
| 敗北条件（全滅） | ✅ | プレイヤーユニット全滅で敗北 |
| ロード不在許容 | ✅ | ロードがいないマップでも敗北にならない |

## 6. AI

| 項目 | 状況 | 備考 |
|------|------|------|
| AGGRESSIVE パターン | ✅ | 最短距離＋最大ダメージ優先 |
| DEFENSIVE パターン | ✅ | 射程内のみ攻撃 |
| GUARD パターン | ✅ | 定位置待機 |
| **CTベースAI実行** | **✅** | **個別ターンでAI自動行動** |
| **陣営ベース敵対判定** | **✅** | **ENEMY→PLAYER+ALLY、ALLY→ENEMY** |
| **全ユニットAI自動行動** | **✅** | **新規追加: PLAYERもAGGRESSIVEパターンで自動行動** |
| **CAUTIOUS パターン（後の先）** | **✅** | **新規追加: 敵の脅威圏外から攻撃を狙う** |
| **SUPPORT パターン（援護）** | **✅** | **新規追加: 味方が攻撃中の敵を優先ターゲット** |
| **FLEE パターン（逃走）** | **✅** | **新規追加: 全敵から最大距離に逃走、攻撃しない** |
| **ユニット作戦設定（UnitTactic）** | **✅** | **新規追加: 部隊編成画面でユニットごとに5種の作戦を設定** |
| **HEAL パターン（回復）** | **✅** | **新規追加: 負傷した味方を優先回復、杖未装備時はAGGRESSIVEにフォールバック** |
| **敵ヒーラー自動検出** | **✅** | **新規追加: isHealingStaff装備の敵は自動HEAL AI** |
| 回復アイテム使用 AI | ❌ | |
| **複数武器切り替え（手動）** | **✅** | **新規追加: WeaponEquipScreen で装備変更可能** |

## 7. 経路探索

| 項目 | 状況 | 備考 |
|------|------|------|
| PathFinder (ダイクストラ) | ✅ | 地形コスト考慮 |
| 移動可能マス計算 (getMovablePositions) | ✅ | |
| 最短経路計算 (findPath) | ✅ | A*、敵ユニットすり抜け防止対応済み |
| ユニット重なり防止 | ✅ | 味方は通過可・敵は不可（getMovablePositions / findPath 両方で統一） |
| 移動不能地形 (WALL) | ✅ | コスト = -1 |

## 8. UI / 描画

| 項目 | 状況 | 備考 |
|------|------|------|
| マップ描画（地形色分け） | ✅ | ShapeRenderer |
| グリッド線描画 | ✅ | |
| ユニット描画（職業別シェイプ） | ✅ | **UnitShapeRenderer: 10兵種×固有形状（陣営カラー+職業シェイプ）** |
| 移動範囲ハイライト | ✅ | 半透明青 |
| ステータスパネル | ✅ | HPバー + CTバー + **EXP表示** |
| HPバー（色分け） | ✅ | 緑/黄/赤 |
| **CTバー（ユニット下部）** | **✅** | **新規追加: 金/水/灰の3段階色分け** |
| **アクティブユニット表示（金色リング）** | **✅** | **新規追加** |
| **行動順キュー（画面左側）** | **✅** | **新規追加: 8ユニット分の行動予測** |
| **ラウンド／アクティブユニット名表示** | **✅** | **新規追加: 画面上部中央** |
| **ユニット調査パネル（タップ情報表示）** | **✅** | **新規追加: 任意ユニットをタップで左下に詳細パネル表示、アクティブユニットパネルとは別表示** |
| ~~フェイズ/ターン表示~~ | **廃止** | ラウンド表示に変更 |
| ~~行動済みユニットの半透明表示~~ | **廃止** | CTバーに置き換え |
| アクション選択メニュー | ❌ | |
| 攻撃範囲表示（赤ハイライト） | ✅ | 移動範囲外の攻撃可能マスを表示 |
| 戦闘予測パネル | ❌ | |
| **移動アニメーション** | **✅** | **新規追加: PathFinder.findPath()で経路取得、タイルごとの補間移動** |
| **AI行動タイミング制御** | **✅** | **新規追加: 思考0.4s + 移動0.15s/タイル + 戦闘0.6s + 後処理0.3s** |
| 戦闘アニメーション | ❌ | |
| マップスクロール / ズーム | ❗ | マップ全体俯瞰表示（ExtendViewport）で画面いっぱいに表示。スクロール/ズームは未実装 |
| 経験値 / レベルアップ演出 | ❌ | |
| **出撃配置入れ替え（BattlePrepScreen）** | **✅** | **新規追加: タップでユニット選択→スポーン位置入れ替え、選択ハイライト表示** |
| リザルト画面（勝利/敗北） | ✅ | タップでWorldMapScreenへ |
| **撤退ボタン（逃げる）** | **✅** | **新規追加: 画面右下に撤退ボタン、確認ダイアログ付き。撤退時は敗北扱い** |
| **回復演出（HEAL_RESULT）** | **✅** | **新規追加: 緑色 "+X HP" オーバーレイ表示、HEAL_RESULTステート** |
| **HEAL作戦色（FormationScreen）** | **✅** | **新規追加: ミントグリーン表示** |
| スプライト・テクスチャ | ❌ | ShapeRenderer のみ |

## 9. 画面遷移

| 項目 | 状況 | 備考 |
|------|------|------|
| TacticsFlameGame メインクラス | ✅ | GameProgress・ScreenManager 統合 |
| **ScreenManager（画面遷移管理）** | **✅** | **新規追加: 画面間の疎結合遷移を一元管理** |
| タイトル画面 (TitleScreen) | ✅ | 実装済み（起動時は経由せず、必要時のみ遷移） |
| **ワールドマップ画面 (WorldMapScreen)** | **✅** | **新規追加: チャプターノード選択・編成ボタン** |
| **部隊編成画面 (FormationScreen)** | **✅** | **ユニット一覧・出撃/非出撃ボタン・詳細パネル（装備後ステータス括弧表示）・作戦設定・装備変更遷移** |
| **戦闘準備画面 (BattlePrepScreen)** | **✅** | **マッププレビュー・ユニット配置・配置入れ替え・出撃開始・ランダム敵生成対応** |
| **ランダムマップ (another_chapter)** | **✅** | **新規追加: 出撃中ユニット平均Lv（整数除算）基準の敵が出現・何度でも挑戦可能・ワールドマップに紫ノードで表示** |
| バトル画面 (BattleScreen) | ✅ | BattleConfig対応・CTベースターン制・**Campaign Modeウェーブ進行対応** |
| **バトルリザルト画面 (BattleResultScreen)** | **✅** | **新規追加: 勝敗表示・生存ユニット・撃破数・チャプタークリア処理** |
| リザルト画面 (ResultScreen) | ⚠️ | レガシー互換用に残存 |
| **武器装備変更画面 (WeaponEquipScreen)** | **✅** | **4スロット装備パネル・パーティ在庫・右手/左手/防具1/防具2装備・二刀流対応・スクロール修正済み** |
| ショップ画面 | ❌ | |
| 会話・ストーリー画面 | ❌ | |

### 画面フロー

```
起動 → WorldMapScreen
   ├── 部隊編成ボタン → FormationScreen → WorldMapScreen
   │                        └── 装備変更ボタン → WeaponEquipScreen → FormationScreen
   └── チャプター選択 → BattlePrepScreen →(出撃)→ BattleScreen
                           ↓
        WorldMapScreen ←── BattleResultScreen ←── BattleScreen(勝敗)
```

## 10. テスト

| 項目 | 状況 | 備考 |
|------|------|------|
| TurnManagerTest | ✅ | **新規追加: 11テストケース** |
| CT蓄積テスト | ✅ | SPD順・持ち越し・ラウンド |
| 行動順予測テスト | ✅ | predictActionOrder |
| エッジケーステスト | ✅ | 空リスト・1体・戦闘不能 |
| **GameUnitExpTest** | **✅** | **新規追加: 経験値加算・レベルアップ・レベル上限・成長テスト** |
| **BattleSystemExpTest** | **✅** | **新規追加: 経験値計算・クランプ・撃破ボーナステスト** |
| **LevelUpSystemTest** | **✅** | **新規追加: 経験値付与・レベルアップフローテスト、アレスLv5シミュレーション検算テスト追加（personalGrowthRate二重加算バグ修正の検証）** |
| **MapLoaderTest** | **✅** | **新規追加: 21テストケース — 全チャプター読み込み・地形・敵・武器・勝利条件・敵レベルアップ能力値検証（Lv1基本値/Lv6クラス成長率/ランダム敵成長率）** |
| **SaveManagerTest** | **✅** | **新規追加: 16テストケース — シリアライズ/デシリアライズ・ファイルi/O・エッジケース** |
| **DeploymentSwapTest** | **✅** | **新規追加: 8テストケース — ユニット入れ替え・空スポーンへの移動・連続入れ替え** |
| **UnitTacticTest** | **✅** | **新規追加: 9テストケース — next()サイクル・デフォルト値・displayName/description検証** |
| **GameUnitEquipmentTest** | **✅** | **新規追加: effectiveSpeed・素手射程・防具装備テスト** |
| **DamageCalcTest** | **✅** | **新規追加: 素手・防具・追撃・三すくみテスト** |
| **BattleSystemCounterTest** | **✅** | **新規追加: 素手反撃・射程判定テスト** |
| **AITacticTest** | **✅** | **新規追加: 9テストケース — CAUTIOUS/SUPPORT/FLEE各パターンの行動検証、MoveAndHeal分岐対応** |
| **VictoryCheckerTest** | **✅** | **14テストケース — 勝利/敗北/ロード不在許容・ボス撃破・ターン防衛** |
| **ClassChangeTest** | **✅** | **新規追加: 転職テスト — stats変化・HP差分調整・転職制限** |
| **UnitClassLoaderTest** | **✅** | **新規追加: 2テストケース — baseStats正常パース検証・baseStats欠如時のデフォルト値検証** |
| **GameProgressTest** | **✅** | **新規追加: 周回システムテスト — ch12クリアでcycle+1・startNewCycle全チャプターリセット・ch1再解放・ランダム/キャンペーンマップ状態維持** |

## 11. サウンド / アセット

| 項目 | 状況 | 備考 |
|------|------|------|
| BGM | ❌ | |
| SE（攻撃・移動・選択） | ❌ | |
| フォント | ✅ | FreeType + NotoSansJP.ttf（漢字ホワイトリスト方式、4サイズ: 24/32/48/64） |
| テクスチャ / スプライト | ❌ | |
| アニメーション | ❌ | |

---

## 12. Campaign Mode（連続マップ進行）

| 項目 | 状況 | 備考 |
|------|------|------|
| **Campaign Map データモデル（WaveConfig, WaveRegion, WaveEnemy）** | **✅** | **新規追加: model/campaign パッケージに3クラス。WaveEnemy に armorId フィールド追加** |
| **WaveManager（ウェーブ進行管理）** | **✅** | **新規追加: system/WaveManager — ウェーブクリア判定・次ウェーブ遷移・敵出現管理** |
| **MapLoader.loadCampaignMap()** | **✅** | **新規追加: campaign_map.json のパース、WaveConfig リスト生成** |
| **BattleScreen ウェーブ状態遷移** | **✅** | **WAVE_CLEAR / WAVE_TRANSITION / WAVE_START の3ステート追加** |
| **BattleScreen 可視範囲カリング** | **✅** | **大マップ最適化: カメラ可視範囲外のタイル・ユニットを描画スキップ** |
| **campaign_map.json** | **✅** | **150×30 タイル、6ウェーブ構成** |
| **WorldMapScreen Campaign ボタン** | **✅** | **新規追加: Campaign Mode 開始ボタン** |
| **BattlePrepScreen Campaign 読み込み対応** | **✅** | **Campaign Map JSON の読み込み・ウェーブ情報付き BattleConfig 生成** |
| **BattleResultScreen Campaign 結果表示** | **✅** | **Campaign 勝利/敗北・ウェーブ進捗・累計撃破数の表示** |
| **BattleConfig isCampaignMode + waves** | **✅** | **BattleConfig に isCampaignMode フラグと waves リストを追加（CampaignConfig は不採用）** |
| 中間セーブ（ウェーブ間のオートセーブ） | ❌ | ウェーブクリア後の自動保存は未実装 |
| VictoryChecker.checkWaveOutcome() の統合 | ❌ | WaveManager と VictoryChecker で二重管理状態。統合が必要 |

---

## 13. 周回（NewGame+）システム

| 項目 | 状況 | 備考 |
|------|------|------|
| **GameProgress.cycle プロパティ** | **✅** | **周回数管理（0=初回、1=2周目...）** |
| **GameProgress.startNewCycle()** | **✅** | **chapter_12クリアで全チャプターリセット、chapter_1再開放、cycle+1** |
| **パーティ引継ぎ** | **✅** | **ユニット・装備・レベルは周回で維持** |
| **敵レベル補正（levelBonus）** | **✅** | **敵の実効レベル = JSONのlevel + (cycle × 10)** |
| **MapLoader levelBonus パラメータ** | **✅** | **loadMap/loadCampaignMap/parseEnemies/parseEnemyUnit/generateRandomEnemies/parseWaveEnemies/createUnitFromWaveEnemy に対応** |
| **SaveManager cycle 永続化** | **✅** | **SAVE_VERSION 6: cycle のシリアライズ/デシリアライズ** |
| **BattlePrepScreen levelBonus 受け渡し** | **✅** | **cycle*10 を loadMap/loadCampaignMap に渡す** |
| **WorldMapScreen 周回数表示** | **✅** | **ヘッダーに「2周目」等を表示** |

---

## 最優先の次ステップ

1. **プレイヤー手動操作モードの復活（オプション）**
   - 現在は全ユニットAI自動行動
   - PLAYERユニットの手動操作/AI切替機能を検討

2. **戦闘予測パネル**
   - 攻撃前にダメージ・命中率・追撃の有無を表示

3. **戦闘アニメーション**
   - 攻撃時のエフェクトやダメージ数値を視覚的に演出

4. ~~**JSONデータ読み込みの統合**~~ ✅ 完了
   - ~~BattlePrepScreen でハードコードのマップ→JSONデータローダーに切り替え~~

5. **マップスクロール**
   - カメラ移動でマップ全体を見渡せるように
