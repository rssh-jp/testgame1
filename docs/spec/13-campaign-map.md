# 13. 連続マップ進行（Campaign Map）仕様

最終更新: 2026年3月9日

---

## 仕様サマリ

Tactics Flame に「連続マップ進行（Campaign Map）」を追加する。従来は各チャプターが独立したマップ（15×10 程度）で完結していたが、本機能により **1 枚の大マップ（150×30）** の上にチャプター1〜6 の地形・敵を配置し、「現チャプターの敵を全滅 → 次のウェーブ（チャプター）の敵がマップ上に出現」という連続進行体験を提供する。

プレイヤーの部隊は大マップの左下から開始し、ウェーブをクリアするたびにカメラが右方向へ自動パンし、新たな敵群が出現する。既存の個別チャプターマップ JSON は**データソース**としてそのまま活用し、Campaign Map 生成ツール（CampaignMapBuilder）がオフセット付きで1枚の大マップに合成する。

---

## 1. 目的と背景

### 何を解決する仕様か

- 現行の「チャプター毎にマップ読み込み → 戦闘 → リザルト → ワールドマップ → 次チャプター選択」という繰り返しが**テンポを損なう**
- 大マップ上での連続進行により、**1 つの戦場を攻略している没入感**を提供する
- ウェーブ形式でチャプターを連結することで、**戦略的なリソース管理**（HP/残りユニット）が重要になる

### プレイヤー体験への効果

- 戦場を制圧していく達成感・征服感
- チャプター間のロードなし → テンポの向上
- HP や CT の持ち越しによる戦略性の深化

---

## 2. 対象範囲 / 非対象範囲

### 今回やること

- Campaign Map JSON フォーマット設計と生成ロジック
- WaveManager によるウェーブ進行管理
- BattleScreen へのウェーブ遷移ステート追加
- ウェーブ間のカメラ自動パンとチャプター名演出
- VictoryChecker のウェーブ対応拡張
- ウェーブ間の中間セーブ
- 可視範囲カリングによるパフォーマンス最適化

### 今回やらないこと（スコープ外）

- 大マップそのものを手動でデザインする専用エディタの構築
- チャプター途中での分岐進行（常に左→右の一方向）
- ウェーブ間でのショップ・編成画面への遷移
- マップ上のイベント（会話・村訪問）の実装
- ウェーブの逆走・任意のウェーブへのジャンプ
- 既存の個別チャプターモードの廃止（Campaign Mode は**別モード**として共存）
- **チャプター7〜12の Campaign Map 統合**（現在の Campaign Map は ch1〜6 で構成。ch7〜12 は個別チャプターモードで実装済みだが、Campaign Map への統合は別途対応予定）

---

## 3. 用語定義

| 用語 | 定義 |
|------|------|
| **Campaign Map** | 全ウェーブの地形を1枚に統合した大マップ（BattleMap インスタンス） |
| **ウェーブ (Wave)** | 1つの旧チャプターに相当する敵群の出現単位。Campaign Map 内の領域を持つ |
| **ウェーブ領域 (WaveRegion)** | 大マップ上の矩形範囲。ウェーブごとの地形・敵配置・スポーンがここに展開される |
| **アクティブウェーブ** | 現在進行中のウェーブ（敵が出現済み） |
| **ウェーブトリガー** | 現ウェーブの勝利条件を満たした際に次ウェーブを開始する仕組み |
| **ウェーブ間演出** | ウェーブクリア時のチャプター名表示・カメラパンなどの演出シーケンス |
| **Campaign Mode** | 連続マップ進行を使用するゲームモード（従来のチャプター選択モードとは別） |
| **オフセット (Offset)** | 個別チャプターの地形を大マップに貼り付ける際の座標ずらし量 |

---

## 4. 機能要件

### 4.1 主成功シナリオ（ユースケース）

1. プレイヤーが WorldMapScreen で「Campaign Mode」を選択する
2. BattlePrepScreen が表示され、出撃ユニットを選択する（通常の出撃準備と同様）
3. BattleScreen に Campaign Map がロードされる
4. カメラが大マップ左下（ウェーブ1 領域）にフォーカスする
5. ウェーブ1 の敵がマップ上に配置済みの状態でバトル開始
6. プレイヤー・敵ユニットが CT ベースで行動する（現行と同一）
7. ウェーブ1 の敵を全滅する
8. **ウェーブ間演出**: 画面にチャプター名「Wave 1 Complete!」が表示される（1.5 秒）
9. **回復フェーズ**: 生存プレイヤーユニットの HP が最大 HP の 30% 回復する
10. **中間セーブ**: ウェーブ進行状態が自動保存される
11. カメラがウェーブ2 領域へスムーズにパン移動する（2.0 秒）
12. ウェーブ2 の敵が出現する（フェードイン）
13. 画面にチャプター名「Wave 2 - 国境の防衛線」が表示される（1.5 秒）
14. バトル再開（CT 進行開始）
15. ウェーブ2〜6 まで同様に繰り返す
16. 最終ウェーブ（ウェーブ6）の敵を全滅する
17. BattleResultScreen に遷移し、Campaign Mode クリアを表示する

### 4.2 代替フロー

| ID | 条件 | 振る舞い |
|----|------|---------|
| AF-1 | プレイヤーのロードが戦闘不能 | 即座に敗北 → BattleResultScreen（キャンペーン失敗）。中間セーブ地点からの再開を提案 |
| AF-2 | プレイヤーユニットが全滅 | 即座に敗北 → BattleResultScreen（キャンペーン失敗） |
| AF-3 | 撤退ボタン押下 | 確認ダイアログ表示。「はい」→ 現在のウェーブまでの進行を保存し敗北扱いでリザルトへ |
| AF-4 | ウェーブ間でアプリ中断 | 中間セーブが既に完了しているため、復帰時にそのウェーブから再開可能 |
| AF-5 | ウェーブ進行中のアプリ強制終了 | 最後の中間セーブ地点から再開（進行中ウェーブは最初からやり直し） |

---

## 5. 状態遷移

### 5.1 Campaign Battle のステート追加

既存の `BattleState` enum に以下を追加する:

```kotlin
enum class BattleState {
    // 既存ステート
    CT_ADVANCING,
    AI_THINKING,
    UNIT_MOVING,
    COMBAT_RESULT,
    HEAL_RESULT,
    POST_ACTION,
    RESULT,
    // 新規: ウェーブ遷移ステート
    WAVE_CLEAR,        // ウェーブクリア演出中
    WAVE_TRANSITION,   // カメラパン + 次ウェーブ準備中
    WAVE_START         // 新ウェーブ開始演出中
}
```

### 5.2 ステート遷移図

```
CT_ADVANCING ──→ AI_THINKING ──→ UNIT_MOVING ──→ COMBAT_RESULT ──→ POST_ACTION
     ↑                                                                    │
     │                                                                    ↓
     ←──────────────────────── 勝敗判定(ONGOING) ─────────────────────────┘
                                      │
                        ウェーブ敵全滅 ↓  (最終ウェーブ以外)
                                 WAVE_CLEAR
                                      │
                              1.5秒経過 ↓
                             WAVE_TRANSITION
                                      │
                           カメラパン完了 ↓
                                WAVE_START
                                      │
                              1.5秒経過 ↓
                               CT_ADVANCING (次ウェーブ)
                                      
                        最終ウェーブ敵全滅 → RESULT (勝利)
                  ロード死亡/全滅         → RESULT (敗北)
```

### 5.3 遷移できない条件と復帰方法

| 遷移 | 制約 | 復帰方法 |
|------|------|---------|
| WAVE_CLEAR → WAVE_TRANSITION | 次ウェーブが存在しない場合は遷移不可 | → RESULT（全Campaign クリア）に遷移 |
| WAVE_TRANSITION → WAVE_START | カメラパンアニメーション未完了 | パン完了まで待機 |
| WAVE_START → CT_ADVANCING | 敵配置アニメーション未完了 | 配置完了まで待機 |
| 任意 → WAVE_CLEAR | 通常チャプターモード（非Campaign） | 通常モードでは WAVE_CLEAR に遷移しない（POST_ACTION → RESULT の従来フロー） |

---

## 6. データ仕様

### 6.1 Campaign Map JSON フォーマット

ファイル名: `campaign_map.json`  
配置先: `core/assets/maps/campaign_map.json`

```json
{
    "id": "campaign_1",
    "name": "Campaign - 炎の進軍",
    "mode": "campaign",
    "width": 150,
    "height": 60,
    "terrain": [
        [0, 0, 1, 0, ...],
        ...
    ],
    "terrainKey": {
        "0": "PLAIN",
        "1": "FOREST",
        "2": "MOUNTAIN",
        "3": "FORT",
        "4": "WATER",
        "5": "WALL",
        "6": "VILLAGE",
        "7": "BRIDGE"
    },
    "playerSpawns": [
        {"x": 2, "y": 5},
        {"x": 2, "y": 7},
        {"x": 3, "y": 6},
        {"x": 3, "y": 8}
    ],
    "waves": [
        {
            "waveId": 1,
            "name": "Wave 1 - 始まりの戦い",
            "sourceChapter": "chapter_1",
            "region": {
                "offsetX": 0,
                "offsetY": 0,
                "width": 25,
                "height": 20
            },
            "enemies": [
                {
                    "id": "w1_enemy_01",
                    "classId": "axeFighter",
                    "name": "山賊A",
                    "level": 1,
                    "x": 18,
                    "y": 5,
                    "ai": "aggressive",
                    "weaponId": "ironAxe"
                }
            ],
            "victoryCondition": {"type": "DEFEAT_ALL"},
            "cameraFocusX": 12,
            "cameraFocusY": 10,
            "healPercent": 30,
            "isLast": false
        },
        {
            "waveId": 2,
            "name": "Wave 2 - 国境の防衛線",
            "sourceChapter": "chapter_2",
            "region": {
                "offsetX": 25,
                "offsetY": 5,
                "width": 25,
                "height": 20
            },
            "enemies": [ ... ],
            "victoryCondition": {"type": "DEFEAT_ALL"},
            "cameraFocusX": 37,
            "cameraFocusY": 15,
            "healPercent": 30,
            "isLast": false
        }
    ]
}
```

### 6.2 既存チャプター JSON との関係

| 項目 | 既存 | Campaign Mode |
|------|------|---------------|
| 地形データ | 各 `chapter_X.json` の `terrain` | Campaign Map JSON の `terrain` に統合（オフセット適用済み） |
| 敵データ | 各 `chapter_X.json` の `enemies` | Campaign Map JSON の `waves[].enemies` に再配置（座標をオフセット） |
| スポーン | 各 `chapter_X.json` の `playerSpawns` | Campaign Map JSON のトップレベル `playerSpawns`（ウェーブ1領域のみ） |
| 勝利条件 | `victoryCondition` | `waves[].victoryCondition`（ウェーブ毎） |
| **既存JSONは変更しない** | readのみ | Campaign Map JSON を新規ファイルとして追加 |

### 6.3 Campaign Map サイズ設計

各チャプターを左→右に配列する。チャプター間には **10 タイル幅の接続地帯**（平地主体 + 砦を配置して回復ポイントとする）を設ける。

| ウェーブ | 元チャプター | 元サイズ | 配置領域サイズ | offsetX | offsetY | 備考 |
|---------|-------------|---------|-------------|---------|---------|------|
| 1 | chapter_1 | 15×10 | 25×20 | 0 | 0 | 元の地形を中央に配置、周囲を平地で埋め |
| 2 | chapter_2 | 15×10 | 25×20 | 25 | 5 | Y方向にずらして蛇行感を演出 |
| 3 | chapter_3 | 15×12 | 25×22 | 50 | 0 | |
| 4 | chapter_4 | 18×12 | 28×22 | 75 | 5 | |
| 5 | chapter_5 | 16×12 | 26×22 | 103 | 0 | |
| 6 | chapter_6 | 20×15 | 30×25 | 129 | 5 | 最終ウェーブ |

**合計マップサイズ: 150×30**（高さは最大 30 で十分。幅は各領域の合計）

> 注: 当初の要望は「10倍」の150×100だが、実際のプレイアビリティとAndroid描画負荷を考慮し、高さは30〜60タイル程度に抑える。幅方向に進行するデザインとする。

### 6.4 データ型定義

#### WaveConfig（新規モデル）

```kotlin
package com.tacticsflame.model.campaign

/**
 * ウェーブ（Campaign Map 内の1チャプター相当）の設定
 *
 * @property waveId ウェーブ番号（1〜N）
 * @property name ウェーブ表示名
 * @property sourceChapter 元チャプターID（参考情報）
 * @property region ウェーブの領域定義
 * @property enemies 敵ユニット配置（大マップ座標）
 * @property victoryConditionType ウェーブの勝利条件
 * @property cameraFocusX カメラフォーカスX座標（タイル）
 * @property cameraFocusY カメラフォーカスY座標（タイル）
 * @property healPercent ウェーブクリア後の回復率（0〜100）
 * @property isLast 最終ウェーブかどうか
 */
data class WaveConfig(
    val waveId: Int,
    val name: String,
    val sourceChapter: String,
    val region: WaveRegion,
    val enemies: List<WaveEnemy>,
    val victoryConditionType: VictoryChecker.VictoryConditionType,
    val cameraFocusX: Int,
    val cameraFocusY: Int,
    val healPercent: Int = 30,
    val isLast: Boolean = false
)
```

#### WaveRegion（新規モデル）

```kotlin
/**
 * ウェーブの矩形領域（大マップ内のオフセットとサイズ）
 *
 * @property offsetX 大マップ上のX開始座標
 * @property offsetY 大マップ上のY開始座標
 * @property width 幅（タイル数）
 * @property height 高さ（タイル数）
 */
data class WaveRegion(
    val offsetX: Int,
    val offsetY: Int,
    val width: Int,
    val height: Int
)
```

#### WaveEnemy（新規モデル）

```kotlin
/**
 * ウェーブ内の敵配置データ（大マップ座標）
 *
 * @property id 敵ユニットID
 * @property classId クラスID
 * @property name 表示名
 * @property level レベル
 * @property x 大マップ上のX座標
 * @property y 大マップ上のY座標
 * @property ai AIパターン
 * @property weaponId 装備武器ID
 * @property isLord ボスフラグ
 */
data class WaveEnemy(
    val id: String,
    val classId: String,
    val name: String,
    val level: Int,
    val x: Int,
    val y: Int,
    val ai: String,
    val weaponId: String,
    val armorId: String = "",
    val isLord: Boolean = false
)
```

#### BattleConfig への Campaign Mode 拡張

当初は独立した `CampaignConfig` を設計していたが、実装では**既存の BattleConfig に `isCampaignMode` フラグと `waves` リストを追加**するアプローチを採用した。これにより BattlePrepScreen → BattleScreen の既存フローを最小限の変更で Campaign Mode に対応させている。

```kotlin
data class BattleConfig(
    val chapterInfo: ChapterInfo,
    val battleMap: BattleMap,
    val playerUnits: List<GameUnit>,
    val playerPositions: Map<String, Position>,
    val enemyUnits: List<GameUnit>,
    val enemyPositions: Map<String, Position>,
    val victoryCondition: VictoryChecker.VictoryConditionType = VictoryChecker.VictoryConditionType.DEFEAT_ALL,
    val isCampaignMode: Boolean = false,
    val waves: List<WaveConfig> = emptyList()
)
```

> **設計変更の経緯**: `CampaignConfig` で `BattleConfig` をラップする方式では、BattleScreen の引数型を変更する必要があり影響範囲が大きかったため、`BattleConfig` を直接拡張する方式に変更した。`isCampaignMode = false`（デフォルト）の場合は従来の個別チャプターモードとして動作し、下位互換性を保つ。

### 6.5 永続化（セーブデータ）

| フィールド | 型 | 説明 | 保存先 |
|-----------|-----|------|--------|
| campaignActive | Boolean | Campaign Mode 進行中か | SharedPreferences |
| currentWaveId | Int | 現在のウェーブ番号 | SharedPreferences |
| clearedWaveIds | List\<Int\> | クリア済みウェーブID | SharedPreferences (JSON) |
| unitStates | List\<UnitSaveData\> | 各ユニットの HP/CT/EXP 等の状態スナップショット | SharedPreferences (JSON) |

既存のセーブデータ v5 との互換性:
- Campaign Mode セーブは **新しいキー** (`campaign_save_v1`) を使用し、既存の `save_data_v5` とは独立
- 既存チャプターモードのセーブには一切影響しない

---

## 7. ロジック仕様

### 7.1 WaveManager（新規 System）

パッケージ: `com.tacticsflame.system`

```kotlin
/**
 * Campaign Mode のウェーブ進行を管理するシステム
 *
 * ウェーブの開始・クリア判定・次ウェーブへの遷移を制御する。
 * BattleScreen から呼び出される。
 */
class WaveManager {

    /** 現在のウェーブインデックス（0始まり） */
    var currentWaveIndex: Int = 0
        private set

    /** ウェーブ設定リスト */
    private var waves: List<WaveConfig> = emptyList()

    /** 現在のウェーブ */
    val currentWave: WaveConfig?
        get() = waves.getOrNull(currentWaveIndex)

    /** 次のウェーブ */
    val nextWave: WaveConfig?
        get() = waves.getOrNull(currentWaveIndex + 1)

    /** 最終ウェーブかどうか */
    val isLastWave: Boolean
        get() = currentWaveIndex >= waves.size - 1

    /** 全ウェーブ数 */
    val totalWaves: Int
        get() = waves.size

    /** 初期化 */
    fun initialize(waveConfigs: List<WaveConfig>) { ... }

    /** 現在のウェーブがクリア条件を満たしたか判定 */
    fun isCurrentWaveCleared(battleMap: BattleMap): Boolean { ... }

    /** 次のウェーブに進む */
    fun advanceToNextWave(): WaveConfig? { ... }

    /** 指定ウェーブの敵をマップに配置する */
    fun spawnWaveEnemies(wave: WaveConfig, battleMap: BattleMap): List<GameUnit> { ... }
}
```

### 7.2 ウェーブクリア判定ロジック

```
【ウェーブクリア判定】
- 実行タイミング: 各ユニット行動完了後（completeUnitAction 内）
- 判定方法:
  1. 現在のウェーブの enemies リストに含まれるユニットIDを照合
  2. それらのうち BattleMap 上に生存しているものが0体 → ウェーブクリア
- 注意: 大マップ上にはクリア済みウェーブの地形は残るが、敵は存在しない
```

### 7.3 ウェーブ間の回復ロジック

```
【回復量計算】
回復量 = floor(maxHp * healPercent / 100)

条件:
- 戦闘不能ユニットは回復しない（蘇生なし）
- 回復後の HP は maxHp を超えない
- CT はリセットしない（持ち越し）
- healPercent のデフォルト値: 30（30%回復）
- 0 の場合は回復なし
```

### 7.4 敵出現ロジック

```
【敵出現シーケンス】
1. 次ウェーブの WaveConfig.enemies を取得
2. 各敵の座標 (x, y) に GameUnit を生成
3. MapLoader の既存ロジック (parseEnemyUnit 相当) でユニットを構築
4. BattleMap.placeUnit() で配置
5. 配置先に既にユニットがいる場合は隣接する空きマスに配置（異常時）
```

### 7.5 カメラパンロジック

```
【カメラ自動パン】
- 開始位置: 現在のカメラ位置
- 終了位置: 次ウェーブの cameraFocusX, cameraFocusY（タイル座標 * TILE_SIZE）
- 補間: EaseInOutCubic（滑らかな加減速）
- 所要時間: 2.0 秒（GameConfig.WAVE_CAMERA_PAN_DURATION）
- パン中はタッチ入力を無視（WAVE_TRANSITION ステートで制御）
```

### 7.6 可視範囲カリング

```
【描画最適化: 可視範囲カリング】

現行の renderMap() は全タイル（15×10 = 150個）を毎フレーム描画している。
Campaign Map では 150×60 = 9000 個のタイルになるため、カリングが必須。

判定方法:
1. カメラの可視矩形をタイル座標に変換
   - minTileX = floor((camera.position.x - viewWidth/2) / TILE_SIZE) - 1
   - maxTileX = ceil((camera.position.x + viewWidth/2) / TILE_SIZE) + 1
   - minTileY, maxTileY も同様
2. ループ範囲を [minTileX, maxTileX] × [minTileY, maxTileY] に制限
3. ユニット描画も同様にカリング（位置がカメラ可視範囲外なら描画スキップ）

期待効果:
- 最大描画タイル数: 画面表示分のみ（約 20×35 = 700 タイル程度）
- 通常チャプターモードでもカリングが適用されるが、全タイルが可視範囲内のためロジック変更の影響なし
```

---

## 8. UI/UX 仕様

### 8.1 Campaign Mode 選択

| 項目 | 仕様 |
|------|------|
| 表示場所 | WorldMapScreen にボタン追加「Campaign Mode」 |
| 表示条件 | チャプター1クリア済み（初回プレイは通常モード推奨） |
| タップ動作 | BattlePrepScreen に遷移（`campaign_map.json` をロード指定） |
| ボタン色 | オレンジ（通常チャプターノードと区別） |

### 8.2 ウェーブ進捗 UI（バトル中）

| 項目 | 仕様 |
|------|------|
| 表示場所 | 画面上部中央（既存のラウンド/ユニット名表示の下） |
| 表示内容 | `Wave 2 / 6`（現在ウェーブ / 全ウェーブ数） |
| フォントサイズ | 20px |
| 表示条件 | Campaign Mode 時のみ（通常チャプターでは非表示） |

### 8.3 ウェーブクリア演出

| タイミング | 内容 | 時間 |
|-----------|------|------|
| ウェーブ敵全滅直後 | 画面中央に「Wave X Complete!」テキスト表示。背景に半透明黒オーバーレイ | 1.5 秒 |
| 回復 | 回復対象ユニットに緑色の「+XX HP」を表示（既存の HEAL_RESULT 表示を流用） | 0.5 秒 |
| カメラ移動 | カメラが次ウェーブ領域にスムーズにパン | 2.0 秒 |
| 敵出現 | 次ウェーブの敵がフェードイン（α: 0→1、0.5秒） | 0.5 秒 |
| ウェーブ開始 | 画面中央に「Wave X - チャプター名」テキスト表示 | 1.5 秒 |
| バトル再開 | CT 進行開始 | — |

合計ウェーブ間演出時間: **約 6.0 秒**

### 8.4 入力操作

| 操作 | 動作 |
|------|------|
| ウェーブ間演出中のタップ | **スキップ可能**: 残りの演出をスキップして次ウェーブの CT 進行を即開始 |
| カメラパン中のピンチ/パン | 無効（WAVE_TRANSITION 中はタッチ無視） |
| パン完了後のカメラ操作 | 通常通りピンチズーム・パンが可能（大マップ全体を探索可能） |
| 撤退ボタン | ウェーブ間演出中は無効。バトル進行中は通常通り機能 |

### 8.5 フィードバック

| 状況 | フィードバック |
|------|--------------|
| ウェーブクリア | SE再生（将来実装）+ 画面テキスト |
| 新ウェーブ開始 | SE再生（将来実装）+ 画面テキスト |
| HP回復 | 緑色テキスト表示（既存フォーマット） |
| 最終ウェーブクリア | 「Campaign Complete!」特別テキスト |

---

## 9. 受け入れ条件（Acceptance Criteria）

### AC-1: ウェーブ進行の基本フロー

```
Given: Campaign Mode でバトル開始、ウェーブ1の敵が3体配置されている
When: ウェーブ1の敵を全て撃破する
Then: WAVE_CLEAR ステートに遷移し、「Wave 1 Complete!」が表示される
And: 1.5秒後にカメラがウェーブ2領域にパン移動を開始する
And: ウェーブ2の敵がマップ上に出現する
And: CT進行が再開される
```

### AC-2: HP 持ち越しと回復

```
Given: ウェーブ1をクリア時にアレス(HP 12/20)、リーナ(HP 5/18)が生存
When: ウェーブ間回復(healPercent=30)が実行される
Then: アレスのHPが 12 + floor(20*0.30) = 12 + 6 = 18 になる
And: リーナのHPが 5 + floor(18*0.30) = 5 + 5 = 10 になる
And: 戦闘不能ユニットは復活しない
```

### AC-3: 敗北条件

```
Given: Campaign Mode でウェーブ3進行中
When: ロード「アレス」が戦闘不能になる
Then: 即座に RESULT ステートに遷移し、敗北として処理される
And: BattleResultScreen に「Campaign Failed - Wave 3」が表示される
```

### AC-4: 通常チャプターモードへの影響なし

```
Given: 通常チャプターモード（chapter_1.json）でバトル中
When: 敵を全滅する
Then: 既存通りに RESULT ステートに遷移する（WAVE_CLEAR は発生しない）
And: BattleResultScreen → WorldMapScreen の従来フローで進行する
```

### AC-5: 中間セーブと復帰

```
Given: Campaign Mode でウェーブ3をクリアして中間セーブが完了
When: アプリを強制終了して再起動する
Then: 「Campaign 続きから」オプションが表示される
And: 選択するとウェーブ4開始時点のユニット状態でバトルが再開される
```

### AC-6: 大マップの描画パフォーマンス

```
Given: 150×30 の Campaign Map が読み込まれている
When: ウェーブ1進行中（カメラはマップ左端付近）
Then: renderMap() で描画されるタイルは可視範囲内のみ（全4500タイルのうち約700タイル）
And: FPS が 30 以上を維持する（Android エミュレータ基準）

> 注: 実装では 150×30 = 4500 タイルとなり、カリングにより画面表示分のみ描画される。
```

### AC-7: 最終ウェーブクリア

```
Given: Campaign Mode のウェーブ6（最終）進行中
When: ウェーブ6の敵を全滅する
Then: 「Campaign Complete!」が表示される
And: BattleResultScreen に全ウェーブの累計情報（総撃破数、生存ユニット数）が表示される
And: GameProgress で全チャプターがクリア済みとしてマークされる
```

---

## 10. テスト観点

### 正常系

| # | テスト項目 | 検証方法 |
|---|----------|---------|
| N-1 | Campaign Map JSON が正しくロードできる | MapLoader ユニットテスト |
| N-2 | WaveManager が正しいウェーブ順序で進行する | WaveManager ユニットテスト |
| N-3 | ウェーブクリア判定が正確（ウェーブ所属の敵のみカウント） | VictoryChecker + WaveManager ユニットテスト |
| N-4 | ウェーブ間 HP 回復量が正確 | ユニットテスト（境界値: maxHP, 0%, 100%） |
| N-5 | 全ウェーブクリアで Campaign Complete になる | 統合テスト |
| N-6 | カメラパンが指定座標に正確に到達する | 手動テスト |
| N-7 | 通常モードで WAVE_CLEAR ステートに遷移しない | 回帰テスト |

### 異常系

| # | テスト項目 | 検証方法 |
|---|----------|---------|
| E-1 | Campaign Map JSON が不正（waves が空配列） | MapLoader テスト — null を返すこと |
| E-2 | 敵配置先にユニットが既に存在する | spawnWaveEnemies テスト — 隣接空きマスに配置 |
| E-3 | ウェーブ進行中にロードが死亡 | 敗北判定が WAVE_CLEAR より優先されること |
| E-4 | 全プレイヤー全滅 + 同ラウンドでウェーブ敵全滅 | 敗北が優先されること |

### 境界値

| # | テスト項目 | 検証方法 |
|---|----------|---------|
| B-1 | ウェーブ1（初回）のクリア | ウェーブ間演出が正常に開始されること |
| B-2 | ウェーブ6（最終）のクリア | RESULT に遷移し WAVE_CLEAR にならないこと |
| B-3 | 回復率 0% のウェーブ | HP が変化しないこと |
| B-4 | 回復率 100% のウェーブ | 全回復すること（maxHP を超えないこと） |
| B-5 | HP が maxHP のユニットに対する回復 | HP が maxHP のまま変化しないこと |
| B-6 | マップ端（x=0, x=149, y=0, y=29）でのユニット配置 | 正常に配置・移動できること |

---

## 11. 影響範囲

### 変更対象の仕様書

| ファイル | 変更内容 |
|---------|---------|
| [01-game-overview.md](01-game-overview.md) | Campaign Mode の記載追加 |
| [02-map-terrain.md](02-map-terrain.md) | マップサイズ上限の変更（最大 160×60）、カリングの記載 |
| [05-battle-system.md](05-battle-system.md) | ウェーブ間回復ロジックの追加 |
| [06-turn-flow.md](06-turn-flow.md) | WAVE_CLEAR/WAVE_TRANSITION/WAVE_START ステートの追加 |
| [08-battle-ui.md](08-battle-ui.md) | ウェーブ進捗 UI、ウェーブ間演出の記載 |
| [09-implementation-status.md](09-implementation-status.md) | Campaign Map 関連の実装状況を追加 |

### 想定変更モジュール

| モジュール | 変更内容 | 影響度 |
|-----------|---------|--------|
| **model/campaign/** | WaveConfig, WaveRegion, WaveEnemy, CampaignConfig 新規追加 | 新規 |
| **system/WaveManager** | ウェーブ進行管理の新規クラス | 新規 |
| **data/MapLoader** | Campaign Map JSON のパース対応。`loadCampaignMap()` メソッド追加 | 中 |
| **screen/BattleScreen** | BattleState に3ステート追加。WaveManager 連携。描画カリング。ウェーブ間演出 | 高 |
| **system/VictoryChecker** | ウェーブ単位のクリア判定メソッド追加 | 低 |
| **model/campaign/GameProgress** | Campaign Mode 進捗管理。中間セーブ対応 | 中 |
| **model/campaign/BattleConfig** | `isCampaignMode` フラグ追加（下位互換: デフォルト false） | 低 |
| **data/SaveManager** | Campaign Mode セーブ/ロード対応 | 中 |
| **screen/WorldMapScreen** | Campaign Mode ボタン追加 | 低 |
| **screen/BattlePrepScreen** | Campaign Map ロード対応 | 低 |
| **screen/BattleResultScreen** | Campaign 結果表示対応（ウェーブ情報） | 低 |
| **core/GameConfig** | 新規定数追加（ウェーブ間演出時間等） | 低 |

---

## 12. 段階導入計画

### Phase 1: MVP（最小実現可能仕様）

**目標**: 2ウェーブの連続進行が動作すること

| # | タスク | 対象 | 備考 |
|---|-------|------|------|
| 1-1 | WaveConfig/WaveRegion/WaveEnemy データクラス作成 | model/campaign | |
| 1-2 | campaign_map.json 作成（ウェーブ1+2の2ウェーブ分） | assets/maps | chapter_1, chapter_2 の地形・敵を手動統合 |
| 1-3 | MapLoader に `loadCampaignMap()` 実装 | data | JSON パース。waves 配列対応 |
| 1-4 | WaveManager 実装 | system | ウェーブ進行 + クリア判定 |
| 1-5 | BattleState に WAVE_CLEAR, WAVE_TRANSITION, WAVE_START 追加 | screen | |
| 1-6 | BattleScreen にウェーブ遷移ロジック追加 | screen | processWaveClear, processWaveTransition, processWaveStart |
| 1-7 | renderMap() に可視範囲カリング追加 | screen | |
| 1-8 | ウェーブ間の HP 回復実装 | system | WaveManager.healSurvivors() |
| 1-9 | WaveManager ユニットテスト | test | |
| 1-10 | 2ウェーブ通しプレイの手動テスト | — | |

### Phase 2: 全ウェーブ統合

| # | タスク | 対象 | 備考 |
|---|-------|------|------|
| 2-1 | campaign_map.json を全6ウェーブに拡張 | assets/maps | |
| 2-2 | CampaignConfig データクラス作成 | model/campaign | |
| 2-3 | BattleConfig に isCampaignMode フラグ追加 | model/campaign | |
| 2-4 | ウェーブ間演出（チャプター名表示・カメラパン）実装 | screen | |
| 2-5 | ウェーブ進捗 UI 実装 | screen | |
| 2-6 | 全6ウェーブ通しプレイの手動テスト + パフォーマンス計測 | — | |

### Phase 3: セーブ・UI強化

| # | タスク | 対象 | 備考 |
|---|-------|------|------|
| 3-1 | Campaign 中間セーブ実装 | data/SaveManager | |
| 3-2 | Campaign 復帰ロジック実装 | screen/BattleScreen | |
| 3-3 | WorldMapScreen に Campaign Mode ボタン追加 | screen | |
| 3-4 | BattleResultScreen の Campaign 対応 | screen | |
| 3-5 | ウェーブ間演出のスキップ機能 | screen | |
| 3-6 | ユニット描画のカリング最適化 | screen | |

### Phase 4: 拡張（将来）

| # | タスク | 備考 |
|---|-------|------|
| 4-1 | Campaign Map 自動生成ツール（CampaignMapBuilder） | 既存チャプター JSON からの自動合成 |
| 4-2 | ウェーブ間ショップ | 報酬金で武器購入 |
| 4-3 | 分岐ルート | ウェーブクリア条件に応じた分岐 |
| 4-4 | ウェーブ難易度スケーリング | パーティレベルに応じた敵強化 |

---

## 13. リスクと未決事項

### リスク

| # | リスク | 影響 | 軽減策 |
|---|-------|------|--------|
| R-1 | 大マップの描画パフォーマンス低下 | FPS 低下 → Android で操作不能 | カリング実装。Phase 1 の MVP 段階で計測し、問題があればタイルサイズ拡大 or マップ縮小 |
| R-2 | ウェーブ間のバランス崩壊（HP持ち越し） | ウェーブ後半で全滅不可避 or 簡単すぎる | healPercent をウェーブ毎に調整可能にしている。テストプレイで調整 |
| R-3 | 中間セーブのデータ量増大 | SharedPreferences 上限（不明） | JSON 圧縮。ユニット状態の差分保存を検討 |
| R-4 | 大マップ JSON ファイルサイズ | terrain 配列が巨大（150×30 = 4,500 要素） | Run-Length Encoding (RLE) 圧縮の導入を将来検討。現状は生 JSON で問題ない（約 50KB 程度） |

### 未決事項（要確認）

| # | 項目 | 判断基準 |
|---|------|---------|
| U-1 | マップの高さ（30 vs 60 vs 100） | MVP の描画パフォーマンス計測結果で決定 |
| U-2 | 戦闘不能ユニットのウェーブ間蘇生有無 | ゲームバランステストで決定。MVP では蘇生なし |
| U-3 | ウェーブ間で CT をリセットするか | MVP では CT 持ち越し。テストプレイで評価 |
| U-4 | ウェーブ間演出のスキップ可否 | Phase 3 で実装予定。MVP はスキップ不可 |
| U-5 | Campaign Mode の開放条件 | 暫定: チャプター1クリア後。全チャプタークリア後に変更する可能性 |

---

## 14. FE シリーズとの比較

| 観点 | FE シリーズ | Tactics Flame Campaign Mode |
|------|-----------|---------------------------|
| **共通点** | チャプター毎の戦場、増援ウェーブ（FE覚醒等） | ウェーブ形式の敵出現、HP持ち越しによる戦略性 |
| **差分** | 各チャプターは独立マップ。増援は同一マップ内 | 全チャプターを1枚の大マップに物理的に連結 |
| **差分によるメリット** | — | 征服感・没入感の向上。チャプター間のロードなし |
| **差分によるデメリット** | — | マップデザインの制約（左→右の一方向）。リソース管理が複雑化。難易度調整が困難 |

---

## `docs/spec/` への追記先提案

| ファイル名 | 節名 | 追加内容 |
|-----------|------|---------|
| `docs/spec/13-campaign-map.md` | （本ファイル） | 本仕様書全体 |
| `docs/spec/01-game-overview.md` | 「ゲームモード」節を新設 | Campaign Mode の概要（3行程度） |
| `docs/spec/02-map-terrain.md` | 「マップサイズ」節を更新 | 最大サイズを 160×60 に変更、カリング仕様への参照リンク |
| `docs/spec/06-turn-flow.md` | 「バトルステート」節を更新 | WAVE_CLEAR/WAVE_TRANSITION/WAVE_START の追加 |
| `docs/spec/09-implementation-status.md` | 「Campaign Map」行を追加 | ❌ 未実装として記載 |
| `docs/spec/README.md` | 仕様一覧に追加 | `13-campaign-map.md` へのリンク |
