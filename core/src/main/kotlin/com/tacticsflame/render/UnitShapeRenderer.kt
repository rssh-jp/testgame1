package com.tacticsflame.render

import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.tacticsflame.model.unit.Faction
import com.tacticsflame.model.unit.GameUnit
import com.tacticsflame.model.unit.UnitClass

/**
 * ユニットの職業ごとに異なる形状を描画するレンダラー
 *
 * 各兵種に固有の形状を割り当て、マップ上でユニットを
 * 視覚的に区別しやすくする。ShapeRenderer（Filledモード）
 * でプリミティブ図形を組み合わせて描画する。
 *
 * | 兵種 | 形状 | 視覚的理由 |
 * |------|------|-----------|
 * | ロード | ◇ ひし形 | 王者の象徴 |
 * | ソードファイター | ▲ 上向き三角 | 剣の切っ先 |
 * | ランサー | △ 細い三角 | 槍の穂先 |
 * | アクスファイター | ■ 正方形 | 力強い安定感 |
 * | アーチャー | ◇ 横長ひし形 | 矢じりの形 |
 * | メイジ | ⬡ 六角形 | 魔法陣 |
 * | ヒーラー | ✚ 十字形 | 回復のシンボル |
 * | ナイト | ⬠ 五角形 | 騎士の盾 |
 * | ペガサスナイト | ↑ 上向き矢印 | 飛行のイメージ |
 * | アーマーナイト | 八角形 | 重厚な鎧 |
 */
object UnitShapeRenderer {

    /**
     * ユニットの陣営カラーをShapeRendererに設定する
     *
     * @param sr ShapeRenderer
     * @param unit 対象ユニット
     */
    private fun setFactionColor(sr: ShapeRenderer, unit: GameUnit) {
        when (unit.faction) {
            Faction.PLAYER -> sr.setColor(0.2f, 0.4f, 1f, 1f)   // 青
            Faction.ENEMY -> sr.setColor(1f, 0.2f, 0.2f, 1f)    // 赤
            Faction.ALLY -> sr.setColor(0.2f, 1f, 0.4f, 1f)     // 緑
        }
    }

    /**
     * ユニットの兵種に応じた形状を描画する
     *
     * ShapeRenderer は Filled モードで呼び出すこと。
     * 陣営カラーの設定も内部で行うため、呼び出し側での色設定は不要。
     *
     * @param sr ShapeRenderer（Filledモード）
     * @param unit 描画対象ユニット
     * @param cx 中心X座標（ピクセル）
     * @param cy 中心Y座標（ピクセル）
     * @param radius 形状の基準半径（tileSize / 3 相当）
     */
    fun drawClassShape(sr: ShapeRenderer, unit: GameUnit, cx: Float, cy: Float, radius: Float) {
        setFactionColor(sr, unit)
        when (unit.unitClass.id) {
            UnitClass.LORD.id -> drawDiamond(sr, cx, cy, radius)
            UnitClass.SWORD_FIGHTER.id -> drawTriangleUp(sr, cx, cy, radius)
            UnitClass.LANCER.id -> drawTallTriangle(sr, cx, cy, radius)
            UnitClass.AXE_FIGHTER.id -> drawSquare(sr, cx, cy, radius)
            UnitClass.ARCHER.id -> drawWideDiamond(sr, cx, cy, radius)
            UnitClass.MAGE.id -> drawHexagon(sr, cx, cy, radius)
            UnitClass.HEALER.id -> drawCross(sr, cx, cy, radius)
            UnitClass.KNIGHT.id -> drawPentagon(sr, cx, cy, radius)
            UnitClass.PEGASUS_KNIGHT.id -> drawArrowUp(sr, cx, cy, radius)
            UnitClass.ARMOR_KNIGHT.id -> drawOctagon(sr, cx, cy, radius)
            else -> sr.circle(cx, cy, radius) // デフォルト: 円
        }
    }

    // ─── 正多角形の共通ヘルパー ───

    /**
     * 正多角形を三角形の扇で描画する
     *
     * @param sr ShapeRenderer（Filledモード）
     * @param cx 中心X座標
     * @param cy 中心Y座標
     * @param r 基準半径
     * @param sides 辺の数
     * @param angleOffset 開始角度オフセット（度）
     * @param scale 半径スケール（デフォルト 1.05f）
     */
    private fun drawRegularPolygon(
        sr: ShapeRenderer, cx: Float, cy: Float, r: Float,
        sides: Int, angleOffset: Float, scale: Float = 1.05f
    ) {
        val r1 = r * scale
        for (i in 0 until sides) {
            val angle1 = 360f / sides * i + angleOffset
            val angle2 = 360f / sides * (i + 1) + angleOffset
            sr.triangle(
                cx, cy,
                cx + r1 * MathUtils.cosDeg(angle1), cy + r1 * MathUtils.sinDeg(angle1),
                cx + r1 * MathUtils.cosDeg(angle2), cy + r1 * MathUtils.sinDeg(angle2)
            )
        }
    }

    // ─── 各兵種の形状描画 ───

    /**
     * ロード: ひし形（ダイヤモンド） — 王者の象徴
     *
     * 上下左右の4頂点を持つ正方形を45°回転した形で描画する。
     */
    private fun drawDiamond(sr: ShapeRenderer, cx: Float, cy: Float, r: Float) {
        val r1 = r * 1.1f
        // 上半分
        sr.triangle(cx, cy + r1, cx - r1, cy, cx + r1, cy)
        // 下半分
        sr.triangle(cx, cy - r1, cx - r1, cy, cx + r1, cy)
    }

    /**
     * ソードファイター: 上向き三角形 — 剣の切っ先
     *
     * やや幅広の正三角形に近い形。
     */
    private fun drawTriangleUp(sr: ShapeRenderer, cx: Float, cy: Float, r: Float) {
        val r1 = r * 1.1f
        sr.triangle(
            cx, cy + r1,                      // 上頂点
            cx - r1 * 0.9f, cy - r1 * 0.7f,   // 左下
            cx + r1 * 0.9f, cy - r1 * 0.7f    // 右下
        )
    }

    /**
     * ランサー: 細い上向き三角形 — 槍の穂先
     *
     * ソードファイターより細長く鋭い形で槍を表現。
     */
    private fun drawTallTriangle(sr: ShapeRenderer, cx: Float, cy: Float, r: Float) {
        val r1 = r * 1.15f
        sr.triangle(
            cx, cy + r1,                       // 上頂点（高い）
            cx - r1 * 0.55f, cy - r1 * 0.8f,  // 左下
            cx + r1 * 0.55f, cy - r1 * 0.8f   // 右下
        )
    }

    /**
     * アクスファイター: 正方形 — 力強い安定感
     *
     * 中心を基準に正方形を描画する。
     */
    private fun drawSquare(sr: ShapeRenderer, cx: Float, cy: Float, r: Float) {
        val side = r * 1.6f
        sr.rect(cx - side / 2, cy - side / 2, side, side)
    }

    /**
     * アーチャー: 横向きひし形 — 矢じりの形
     *
     * ロードのひし形より横が広く縦が短い形。
     */
    private fun drawWideDiamond(sr: ShapeRenderer, cx: Float, cy: Float, r: Float) {
        val rw = r * 1.2f  // 横幅（広め）
        val rh = r * 0.8f  // 縦幅（狭め）
        // 上半分
        sr.triangle(cx, cy + rh, cx - rw, cy, cx + rw, cy)
        // 下半分
        sr.triangle(cx, cy - rh, cx - rw, cy, cx + rw, cy)
    }

    /**
     * メイジ: 六角形 — 魔法陣のイメージ
     *
     * 上頂点から開始する正六角形。
     */
    private fun drawHexagon(sr: ShapeRenderer, cx: Float, cy: Float, r: Float) =
        drawRegularPolygon(sr, cx, cy, r, sides = 6, angleOffset = 90f)

    /**
     * ヒーラー: 十字形 — 回復のシンボル
     *
     * 縦横2本の帯を交差させて十字を形成する。
     */
    private fun drawCross(sr: ShapeRenderer, cx: Float, cy: Float, r: Float) {
        val arm = r * 1.1f    // 腕の長さ（他の形状と同等のスケール）
        val width = r * 0.55f // 腕の太さ
        // 横棒
        sr.rect(cx - arm, cy - width / 2, arm * 2, width)
        // 縦棒
        sr.rect(cx - width / 2, cy - arm, width, arm * 2)
    }

    /**
     * ナイト: 五角形 — 騎士の盾
     *
     * 上頂点から開始する正五角形。
     */
    private fun drawPentagon(sr: ShapeRenderer, cx: Float, cy: Float, r: Float) =
        drawRegularPolygon(sr, cx, cy, r, sides = 5, angleOffset = 90f, scale = 1.1f)

    /**
     * ペガサスナイト: 上向き矢印 — 飛行のイメージ
     *
     * 上向き三角形（矢じり）と下部の細い軸を組み合わせて矢印を形成する。
     */
    private fun drawArrowUp(sr: ShapeRenderer, cx: Float, cy: Float, r: Float) {
        val r1 = r * 1.1f
        // 矢じり部分（上向き三角形）
        sr.triangle(
            cx, cy + r1,
            cx - r1 * 0.9f, cy,
            cx + r1 * 0.9f, cy
        )
        // 矢の軸（下部の細い四角）
        val shaftWidth = r * 0.4f
        sr.rect(cx - shaftWidth / 2, cy - r1 * 0.8f, shaftWidth, r1 * 0.85f)
    }

    /**
     * アーマーナイト: 八角形 — 重厚な鎧
     *
     * 辺が水平・垂直に並ぶ正八角形。
     */
    private fun drawOctagon(sr: ShapeRenderer, cx: Float, cy: Float, r: Float) =
        drawRegularPolygon(sr, cx, cy, r, sides = 8, angleOffset = 22.5f)
}
