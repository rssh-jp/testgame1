# 11. ジョブチェンジ（クラスチェンジ）仕様

## 概要

ユニットの兵種（クラス）を変更する機能。部隊編成画面（FormationScreen）から専用のクラスチェンジ画面（ClassChangeScreen）に遷移し、変更先クラスを選択して実行する。

既存の `GameUnit.changeClass()` メソッドを活用し、UI導線の追加と専用画面の新規作成を行う。

---

## 1. ジョブチェンジの制約ルール

| # | ルール | 根拠 |
|---|--------|------|
| R1 | **ロード（`isLord = true`）はジョブチェンジ不可** | 主人公専用クラスのため。ボタン自体をグレーアウトし、タップしても無反応とする |
| R2 | **現在と同じクラスは選択不可** | 一覧でグレーアウト表示。タップしても何も起きない |
| R3 | **変更先で装備不可の武器は自動外し** | `changeClass()` 内で対応済み。右手・左手それぞれチェック |
| R4 | **二刀流非対応クラスへの変更時は左手武器を自動外し** | `changeClass()` 内で対応済み |
| R5 | **ジョブチェンジ後もレベル・経験値・ステータス（baseStats / levelUpStats）は維持** | 蓄積済みの成長は失われない |
| R6 | **次回レベルアップから新クラスの `classGrowthRate` が適用される** | 成長率 = 個人成長率 + クラス成長率 |
| R7 | **戦闘中（BattleScreen）ではジョブチェンジ不可** | FormationScreen のみで使用可能 |
| R8 | **全10クラスが変更先候補として表示される（ロード含む）** | ロードは `isLord` の制約で選択自体が不可。ロード以外のユニットも lord クラスは候補として表示されるが、主人公専用であるため選択不可とする |

### R8 補足: ロードクラスの選択制限

- `isLord = true` のユニット → そもそもジョブチェンジ画面に遷移できない（FormationScreen でボタン非活性）
- `isLord = false` のユニット → クラス一覧に lord は表示するが**グレーアウト＋「主人公専用」ラベル**で選択不可

---

## 2. UI配置仕様

### 2.1 FormationScreen — 「ジョブチェンジ」ボタン追加

既存の詳細パネル（960×500, Y=170）のボタン行（Y=190f）に4つ目のボタンを追加する。

#### 現在のボタン配置（Y=190f 共通）

| ボタン | X座標 | W | 役割 |
|--------|-------|---|------|
| 作戦変更 | `panelX + 20` (=80) | 380 | 左端 |
| 出撃する/外す | `中央揃え` (=440) | 200 | 中央 |
| 装備変更 | `panelX + 960 - 260 - 20` (=740) | 260 | 右端 |

#### 変更後のボタン配置

ボタン行を2段にする。既存3ボタンの上（Y=270f）に追加行を設ける。

| ボタン | X座標 | Y座標 | W | H | 備考 |
|--------|-------|-------|---|---|------|
| 作戦変更 | 80 | 190 | 380 | 65 | 既存（変更なし） |
| 出撃する/外す | 440 | 190 | 200 | 65 | 既存（変更なし） |
| 装備変更 | 740 | 190 | 260 | 65 | 既存（変更なし） |
| **ジョブチェンジ** | **80** | **270** | **280** | **65** | **新規追加** |

> **注**: `isLord` ユニット選択時はボタンを暗色＋テキスト色 `DARK_GRAY` で描画し、タップ判定を無効化する。

#### ボタン領域定数（FormationScreen に追加）

```kotlin
/** ジョブチェンジボタンの領域（詳細パネル内・左上） */
private val classChangeButtonW = 280f
private val classChangeButtonH = 65f
private val classChangeButtonX = GameConfig.VIRTUAL_WIDTH / 2f - 480f + 20f
private val classChangeButtonY = 270f
```

### 2.2 ClassChangeScreen レイアウト

仮想解像度 1080×1920（縦画面）内で以下のレイアウトとする。WeaponEquipScreen と同様の設計パターン。

```
┌──────────────────────────────────────┐ Y=1920
│           ジョブチェンジ              │ ← ヘッダー（タイトル + ユニット名）
│                                      │    Y=1820〜1920
├──────────────────────────────────────┤
│  ┌──────────────────────────────────┐│
│  │  現在のクラス情報パネル           ││ ← 現クラスパネル
│  │  [クラス名] [移動タイプ] [MOV]   ││    Y=1400〜1780
│  │  装備可能武器 / 二刀流可否        ││
│  │  クラス成長率                     ││
│  └──────────────────────────────────┘│
├──────────────────────────────────────┤
│  変更先クラス一覧                     │ ← リスト領域
│  ┌──────────────────────────────────┐│    Y=180〜1360（スクロール可能）
│  │ ● ロード        [主人公専用]     ││
│  │ ● ソードファイター  → 選択      ││ ← 各行 H=100
│  │ ● ランサー         → 選択      ││
│  │ ● アクスファイター  → 選択      ││
│  │ ● アーチャー       → 選択      ││
│  │ ● メイジ           → 選択      ││
│  │ ● ヒーラー         → 選択      ││
│  │ ● ナイト           → 選択      ││
│  │ ● ペガサスナイト   → 選択      ││
│  │ ● アーマーナイト   → 選択      ││
│  └──────────────────────────────────┘│
├──────────────────────────────────────┤
│ [← 戻る]                [変更実行] │ ← 下部ボタン Y=20〜80
└──────────────────────────────────────┘ Y=0
```

#### レイアウト定数

```kotlin
companion object {
    private const val TAG = "ClassChangeScreen"

    // 現クラス情報パネル
    private const val INFO_PANEL_X = 60f
    private const val INFO_PANEL_Y = 1400f
    private const val INFO_PANEL_W = 960f
    private const val INFO_PANEL_H = 380f

    // クラスリスト
    private const val LIST_TOP = 1360f
    private const val LIST_BOTTOM = 100f
    private const val SLOT_HEIGHT = 100f
    private const val SLOT_GAP = 6f
    private const val SLOT_WIDTH = 960f
    private const val SLOT_X = 60f
}
```

---

## 3. 画面フロー

```
FormationScreen
  │
  │ [ユニット選択] → 詳細パネル表示
  │
  │ [ジョブチェンジ] ボタンタップ ←── isLord なら無効（グレーアウト）
  │
  ▼
ClassChangeScreen
  │
  │  現在のクラス情報を上部に表示
  │  変更可能なクラス一覧を表示
  │    - 現在のクラス → グレーアウト（「現在」ラベル表示）
  │    - lord クラス → グレーアウト（「主人公専用」ラベル表示）※isLord=false の場合
  │    - その他 → 選択可能（タップでハイライト）
  │
  │ [クラスを選択] → 選択クラスをハイライト + 下部に「変更実行」ボタン活性化
  │
  │ [変更実行] ボタンタップ
  │   ├─ 確認ダイアログ表示
  │   │   「○○ を [新クラス名] に変更しますか？」
  │   │   [はい]  [いいえ]
  │   │
  │   ├─ [はい] → GameUnit.changeClass(newClass) を実行
  │   │          → ログ出力: "${unit.name}: クラス変更 ${oldClass.name} → ${newClass.name}"
  │   │          → FormationScreen に自動遷移（戻る）
  │   │
  │   └─ [いいえ] → ダイアログ閉じ、選択状態を維持
  │
  │ [← 戻る] ボタンタップ → FormationScreen へ戻る
  │
  ▼
FormationScreen（選択ユニットの兵種が更新されている）
```

---

## 4. ClassChangeScreen の表示内容

### 4.1 ヘッダー領域（Y=1820〜1920）

| 項目 | 内容 |
|------|------|
| タイトル | `"— ジョブチェンジ —"` （titleFont, 中央揃え） |
| ユニット名 | `"${unit.name}  Lv.${unit.level}"` （font, 中央揃え） |

### 4.2 現クラス情報パネル（Y=1400〜1780）

半透明黒背景 + 青枠線。以下の情報を表示する。

| 行 | 内容 | フォント | 色 |
|----|------|----------|-----|
| 1 | `"現在のクラス: ${unitClass.name}"` | font | CYAN |
| 2 | `"移動タイプ: ${moveType.name}  MOV: ${baseMov}"` | smallFont | LIGHT_GRAY |
| 3 | `"装備可能: ${usableWeapons.joinToString(", ") { weaponTypeName(it) }}"` | smallFont | WHITE |
| 4 | `"二刀流: ${if (canDualWield) "可能 (ペナルティ:${dualWieldPenalty})" else "不可"}"` | smallFont | WHITE |
| 5 | `"クラス成長率:"` | smallFont | GOLD |
| 6 | HP/STR/MAG/SKL を横並び | smallFont | WHITE |
| 7 | SPD/LCK/DEF/RES を横並び | smallFont | WHITE |

#### 選択中のクラス情報表示

クラス一覧で変更先クラスを選択中の場合、現クラス情報の右側（または下側）に変更先クラスの情報を並べて表示し、差分を確認できるようにする。

| 並列表示 | 左列: 現在のクラス | 右列: 変更先クラス |
|----------|-------------------|-------------------|
| MOV | `5` | `7` (変化があれば色付き) |
| 装備可能 | 剣 | 剣, 槍 |
| 成長率 | 各数値 | 各数値（上昇↑緑 / 下降↓赤） |

### 4.3 クラス一覧リスト（Y=100〜1360、スクロール対応）

各クラスを行として表示する。1行のレイアウト（H=100）:

```
┌─────────────────────────────────────────────────┐
│ [色アイコン]  クラス名    移動:xx  MOV:x        │
│              装備: 剣, 槍          [状態ラベル]  │
└─────────────────────────────────────────────────┘
```

#### 行の状態

| 状態 | 背景色 | テキスト色 | 状態ラベル | タップ可否 |
|------|--------|-----------|-----------|----------|
| 選択可能 | 暗青 (0.1, 0.15, 0.25) | WHITE | なし | ○ |
| 選択中（ハイライト） | 明青 (0.2, 0.3, 0.5) | WHITE | なし | ○ |
| 現在のクラス | 暗灰 (0.15, 0.15, 0.15) | DARK_GRAY | `"現在"` | × |
| 主人公専用（lord） | 暗灰 (0.15, 0.15, 0.15) | DARK_GRAY | `"主人公専用"` | × |

### 4.4 下部ボタン領域（Y=20〜80）

| ボタン | X | Y | W | H | 条件 |
|--------|---|---|---|---|------|
| ← 戻る | 40 | 20 | 180 | 60 | 常に有効 |
| 変更実行 | VIRTUAL_WIDTH - 320 | 20 | 280 | 60 | クラス選択時のみ有効。未選択時はグレーアウト |

### 4.5 確認ダイアログ

画面中央にモーダルオーバーレイ（半透明黒背景）で表示する。

```
┌──────────────────────────────────┐
│                                  │
│    [ユニット名] を               │
│    [新クラス名] に変更しますか？  │
│                                  │
│     [はい]       [いいえ]        │
│                                  │
└──────────────────────────────────┘
```

| 要素 | サイズ | 座標 |
|------|--------|------|
| ダイアログ背景 | W=600, H=280 | 中央揃え (X=240, Y=820) |
| 「はい」ボタン | W=160, H=60 | ダイアログ内左寄り |
| 「いいえ」ボタン | W=160, H=60 | ダイアログ内右寄り |

#### 武器武器Typeの日本語変換ヘルパー

```kotlin
/** 武器タイプの日本語名を返す */
private fun weaponTypeName(type: WeaponType): String = when (type) {
    WeaponType.SWORD -> "剣"
    WeaponType.LANCE -> "槍"
    WeaponType.AXE   -> "斧"
    WeaponType.BOW   -> "弓"
    WeaponType.MAGIC -> "魔法"
    WeaponType.STAFF -> "杖"
}
```

---

## 5. 受け入れ条件（テスト可能な条件）

### 5.1 モデル層（既存テスト ClassChangeTest で大部分カバー済み）

| # | 条件 | テスト方法 | 状態 |
|---|------|-----------|------|
| AC-M1 | `changeClass()` で `unitClass` が変更される | `ClassChangeTest.changeClassでunitClassが変更されること` | ✅ 実装済み |
| AC-M2 | 転職後のレベルアップで新クラスの `classGrowthRate` が適用される | `ClassChangeTest.changeClass後のレベルアップで…` | ✅ 実装済み |
| AC-M3 | 転職時に装備不可の武器が予備に移動される | `ClassChangeTest.転職時に装備不可の武器が…` | ✅ 実装済み |
| AC-M4 | 転職時に `baseStats` / `levelUpStats` が維持される | `ClassChangeTest.転職時にbaseStatsと…` | ✅ 実装済み |
| AC-M5 | 二刀流非対応クラスへの転職で左手が外される | `ClassChangeTest.二刀流非対応クラスに…` | ✅ 実装済み |

### 5.2 UI層（新規テスト / 手動確認）

| # | 条件 | テスト方法 |
|---|------|-----------|
| AC-U1 | FormationScreen でユニット選択時、「ジョブチェンジ」ボタンが表示される | 画面確認 |
| AC-U2 | `isLord = true` のユニット選択時、ジョブチェンジボタンがグレーアウトされタップ不可 | ロードユニットを選択して確認 |
| AC-U3 | ジョブチェンジボタンタップで ClassChangeScreen に遷移する | 非ロードユニットで確認 |
| AC-U4 | ClassChangeScreen で現在のクラス情報が正しく表示される | 画面確認 |
| AC-U5 | 現在のクラスがグレーアウトされ選択不可 | 画面確認 |
| AC-U6 | lord クラスが `isLord=false` ユニットに対してグレーアウト＋「主人公専用」表示 | 画面確認 |
| AC-U7 | クラスをタップするとハイライトされ、「変更実行」ボタンが活性化する | 画面確認 |
| AC-U8 | 「変更実行」タップで確認ダイアログが表示される | 画面確認 |
| AC-U9 | 確認ダイアログ「はい」で `changeClass()` が実行され FormationScreen に戻る | 画面確認 + ログ確認 |
| AC-U10 | 確認ダイアログ「いいえ」でダイアログが閉じ選択状態を維持 | 画面確認 |
| AC-U11 | 「← 戻る」で FormationScreen に戻る（変更なし） | 画面確認 |
| AC-U12 | 転職後、FormationScreen のスロットに新クラス名が反映されている | 画面確認 |
| AC-U13 | 転職で武器が外れた場合、装備表示が更新されている | 剣装備ユニットをアーチャーに変更して確認 |

### 5.3 画面遷移（ScreenManager 層）

| # | 条件 | テスト方法 |
|---|------|-----------|
| AC-N1 | `ScreenManager.navigateToClassChange(unit)` でClassChangeScreen が表示される | 単体テスト（ScreenType.CLASS_CHANGE のケース追加） |
| AC-N2 | `TacticsFlameGame.classChangeTarget` に対象ユニットがセットされる | プロパティ確認 |
| AC-N3 | ClassChangeScreen の「戻る」で `navigateToFormation()` が呼ばれる | ログ確認 |

---

## 6. 変更対象ファイル一覧と変更概要

### 6.1 新規作成

| ファイル | 概要 |
|----------|------|
| `core/src/main/kotlin/com/tacticsflame/screen/ClassChangeScreen.kt` | ジョブチェンジ専用画面。WeaponEquipScreen と同様のパターンで実装 |
| `core/src/test/kotlin/com/tacticsflame/screen/ClassChangeScreenTest.kt` | ClassChangeScreen のユニットテスト（任意） |

### 6.2 既存ファイル変更

| ファイル | 変更内容 |
|----------|----------|
| [TacticsFlameGame.kt](core/src/main/kotlin/com/tacticsflame/TacticsFlameGame.kt) | `classChangeTarget: GameUnit?` プロパティを追加（画面間受け渡し用） |
| [ScreenManager.kt](core/src/main/kotlin/com/tacticsflame/core/ScreenManager.kt) | `navigateToClassChange(unit)` メソッド追加、`ScreenType.CLASS_CHANGE` 追加、`createScreen()` に ClassChangeScreen のケース追加 |
| [FormationScreen.kt](core/src/main/kotlin/com/tacticsflame/screen/FormationScreen.kt) | ジョブチェンジボタンの追加（定数・入力判定・描画） |

### 6.3 変更なし（既存のまま利用）

| ファイル | 理由 |
|----------|------|
| [GameUnit.kt](core/src/main/kotlin/com/tacticsflame/model/unit/GameUnit.kt) | `changeClass()` が既に実装済み。追加変更不要 |
| [UnitClass.kt](core/src/main/kotlin/com/tacticsflame/model/unit/UnitClass.kt) | 変更不要。`UnitClass.ALL` で全クラス一覧を取得可能 |
| [ClassChangeTest.kt](core/src/test/kotlin/com/tacticsflame/model/unit/ClassChangeTest.kt) | モデル層のテストは既に十分にカバー |

---

## 7. 変更詳細

### 7.1 TacticsFlameGame.kt

```kotlin
// 追加プロパティ（weaponEquipTarget と同じパターン）
/** ジョブチェンジ対象ユニット（FormationScreen → ClassChangeScreen 間の受け渡し） */
var classChangeTarget: GameUnit? = null
```

### 7.2 ScreenManager.kt

```kotlin
// ScreenType に追加
CLASS_CHANGE

// navigateToClassChange メソッド追加
/**
 * ジョブチェンジ画面へ遷移する
 *
 * @param unit ジョブチェンジ対象のユニット
 */
fun navigateToClassChange(unit: GameUnit) {
    Gdx.app.log(TAG, "画面遷移: → ClassChange (${unit.name})")
    game.classChangeTarget = unit
    game.setScreen(createScreen(ScreenType.CLASS_CHANGE))
}

// createScreen に追加
ScreenType.CLASS_CHANGE -> com.tacticsflame.screen.ClassChangeScreen(
    game,
    requireNotNull(game.classChangeTarget) { "classChangeTarget が null です" }
)
```

### 7.3 FormationScreen.kt

```kotlin
// ===== 定数追加 =====
/** ジョブチェンジボタンの領域（詳細パネル内・2段目左） */
private val classChangeButtonW = 280f
private val classChangeButtonH = 65f
private val classChangeButtonX = GameConfig.VIRTUAL_WIDTH / 2f - 480f + 20f
private val classChangeButtonY = 270f

// ===== handleInput() に追加 =====
// ジョブチェンジボタン判定（ユニット選択中かつ非ロードのみ有効）
if (selectedUnit != null && !selectedUnit!!.isLord &&
    touchX in classChangeButtonX..(classChangeButtonX + classChangeButtonW) &&
    touchY in classChangeButtonY..(classChangeButtonY + classChangeButtonH)
) {
    val unit = selectedUnit!!
    Gdx.app.log(TAG, "クラスチェンジ画面へ: ${unit.name}")
    game.screenManager.navigateToClassChange(unit)
    return
}

// ===== renderDetailPanel() に追加 =====
// ジョブチェンジボタン
renderClassChangeButton(unit)

// ===== 新規メソッド =====
/**
 * ジョブチェンジボタンを描画する
 *
 * @param unit 選択中のユニット
 */
private fun renderClassChangeButton(unit: GameUnit) {
    val canChange = !unit.isLord

    shapeRenderer.projectionMatrix = viewport.camera.combined
    Gdx.gl.glEnable(GL20.GL_BLEND)
    shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
    if (canChange) {
        shapeRenderer.setColor(0.35f, 0.2f, 0.5f, 0.9f)  // 紫系
    } else {
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.6f)    // グレー
    }
    shapeRenderer.rect(classChangeButtonX, classChangeButtonY, classChangeButtonW, classChangeButtonH)
    shapeRenderer.end()
    shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
    shapeRenderer.color = if (canChange) Color(0.7f, 0.4f, 1f, 1f) else Color.DARK_GRAY
    shapeRenderer.rect(classChangeButtonX, classChangeButtonY, classChangeButtonW, classChangeButtonH)
    shapeRenderer.end()
    Gdx.gl.glDisable(GL20.GL_BLEND)

    batch.projectionMatrix = viewport.camera.combined
    batch.begin()
    font.color = if (canChange) Color.WHITE else Color.DARK_GRAY
    val label = "ジョブチェンジ"
    glyphLayout.setText(font, label)
    font.draw(
        batch, label,
        classChangeButtonX + classChangeButtonW / 2f - glyphLayout.width / 2f,
        classChangeButtonY + classChangeButtonH / 2f + 12f
    )
    batch.end()
}
```

### 7.4 ClassChangeScreen.kt — 構成概要

```kotlin
class ClassChangeScreen(
    private val game: TacticsFlameGame,
    private val unit: GameUnit
) : ScreenAdapter() {

    // --- UI状態 ---
    /** 選択中のクラスインデックス（-1 = 未選択） */
    private var selectedClassIndex: Int = -1

    /** 確認ダイアログ表示中か */
    private var showConfirmDialog: Boolean = false

    /** UnitClass.ALL の値リスト（表示順序固定） */
    private val allClasses: List<UnitClass> = UnitClass.ALL.values.toList()

    // --- ライフサイクル ---
    override fun show() { /* batch, shapeRenderer, font 初期化 */ }
    override fun render(delta: Float) {
        handleInput()
        // 背景クリア
        renderHeader()
        renderInfoPanel()
        renderClassList()
        renderBottomButtons()
        if (showConfirmDialog) renderConfirmDialog()
    }
    override fun resize(w: Int, h: Int) { viewport.update(w, h, true) }
    override fun dispose() { batch.dispose(); shapeRenderer.dispose() }

    // --- 入力処理 ---
    private fun handleInput() {
        // 確認ダイアログ表示中: はい/いいえ のみ処理
        // 通常: 戻るボタン / 変更実行ボタン / クラスリストタップ
    }

    // --- クラス選択可否判定 ---
    /** 指定クラスが選択可能かを返す */
    private fun isClassSelectable(unitClass: UnitClass): Boolean {
        // 現在のクラスと同じ → false
        if (unitClass.id == unit.unitClass.id) return false
        // lord かつ isLord でない → false（主人公専用）
        if (unitClass.id == "lord" && !unit.isLord) return false
        return true
    }

    // --- 変更実行 ---
    private fun executeClassChange() {
        val newClass = allClasses[selectedClassIndex]
        val oldClassName = unit.unitClass.name
        unit.changeClass(newClass)
        Gdx.app.log(TAG, "${unit.name}: クラス変更 $oldClassName → ${newClass.name}")
        game.screenManager.navigateToFormation()
    }

    // --- 描画メソッド ---
    private fun renderHeader() { /* タイトル + ユニット名 */ }
    private fun renderInfoPanel() { /* 現クラス情報 + 選択クラス比較 */ }
    private fun renderClassList() { /* 全クラス一覧 */ }
    private fun renderBottomButtons() { /* 戻る + 変更実行 */ }
    private fun renderConfirmDialog() { /* モーダル確認 */ }
}
```

---

## 8. データフロー図

```
FormationScreen                TacticsFlameGame              ClassChangeScreen
     │                              │                              │
     │  navigateToClassChange(unit) │                              │
     │ ─────────────────────────── >│                              │
     │                              │  classChangeTarget = unit    │
     │                              │  setScreen(ClassChange)      │
     │                              │ ────────────────────────── > │
     │                              │                              │
     │                              │                  show()      │
     │                              │                  render()    │
     │                              │                              │
     │                              │     unit.changeClass(new)    │
     │                              │ < ────────────────────────── │
     │                              │                              │
     │                              │  navigateToFormation()       │
     │ < ───────────────────────── │                              │
     │                              │                              │
     │  (unitClass が更新済み)       │                              │
```

---

## 9. 将来の拡張ポイント

| 項目 | 説明 |
|------|------|
| クラスチェンジ条件 | レベル制限（例: Lv.10以上で上位職へ）、アイテム消費（転職の証）などの追加 |
| 上位職（プロモーション） | 基本10職 → 上位10職のツリー構造。`UnitClass` に `promotesTo: List<String>` を追加 |
| クラスボーナス | 転職時にステータスボーナス付与（上位職への昇格特典） |
| 演出 | クラスチェンジ時のアニメーション・効果音 |
| セーブデータ | クラスチェンジ履歴の保存（`SaveManager` 対応。現在は `unitClass.id` を保存しているため基本対応済み） |
