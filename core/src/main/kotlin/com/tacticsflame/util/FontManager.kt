package com.tacticsflame.util

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter
import com.badlogic.gdx.utils.Disposable

/**
 * フォント管理ユーティリティ
 *
 * FreeType を使用して TTF フォントから日本語対応の BitmapFont を生成・管理する。
 * アプリケーション全体で共有されるシングルトン。
 */
object FontManager : Disposable {

    /** フォントファイルのパス */
    private const val FONT_PATH = "fonts/NotoSansJP.ttf"

    /**
     * 生成に含める日本語文字セット
     *
     * ゲーム内で使用される文字を網羅する。
     * ひらがな・カタカナ・基本漢字・記号を含む。
     */
    private val JAPANESE_CHARS: String by lazy {
        buildString {
            // ASCII（基本英数字・記号）
            append(FreeTypeFontGenerator.DEFAULT_CHARS)
            // ひらがな
            for (c in '\u3040'..'\u309F') append(c)
            // カタカナ
            for (c in '\u30A0'..'\u30FF') append(c)
            // 全角記号・句読点
            for (c in '\u3000'..'\u303F') append(c)
            // 半角カタカナ・全角英数
            for (c in '\uFF00'..'\uFFEF') append(c)
            // ゲーム内で使用する漢字
            append(GAME_KANJI)
        }
    }

    /**
     * ゲーム内で使用する漢字
     *
     * ユニット名・武器名・UI表示・地形名・メッセージ等に使われる漢字を列挙。
     * 必要に応じて追加する。
     */
    private const val GAME_KANJI =
        // 戦闘・ステータス関連
        "力魔技速幸運守備抵抗移動攻撃命中威回避必殺重量射程距離経験値" +
        "体力残上昇成長率最大小限界突破発動効果追撃反" +
        // ユニット・兵種関連
        "剣士槍斧弓騎兵馬飛天聖魔導師僧侶盗賊山賊将軍竜王子姫" +
        "勇者英雄傭兵暗殺戦闘員隊長副官" +
        // 武器・アイテム関連
        "鉄鋼銀炎氷雷風光闇神聖杖薬傷短刀細身手裏投矢弩" +
        "紋章宝玉鍵扉箱指輪護符盾鎧兜靴腕輪" +
        // 地形関連
        "平原森林山岳砦水壁村橋荒廃墟門城塞塔洞窟海岸砂漠沼地丘陵" +
        // UI・ターン関連
        "行動順番予測味方敵同盟選択了待機装備交換使用捨持物" +
        "開始終止勝利敗北引分撤退進軍配陣形出撃準完全" +
        // マップ・シナリオ関連
        "章節場面会話台詞作名前称号位階級" +
        // 数量・方向
        "一二三四五六七八九十百千万無限個本枚回目番" +
        "東西南北左右中央前後表裏内外" +
        // 状態・属性
        "生死傷毒眠石混乱沈黙封印呪解除復活消滅強化弱" +
        // 接続詞・助詞（テキストメッセージ用）
        "的在是不有和与及或也但而且因為所以" +
        "日月火水木金土年時分秒間" +
        "確認取消保存読込新続再設定音量画質言語" +
        // ターン表示
        "現相手味側自分対象範囲全体単"

    private var generator: FreeTypeFontGenerator? = null
    private val fontCache = mutableMapOf<Int, BitmapFont>()

    /**
     * 指定サイズの日本語対応 BitmapFont を取得する
     *
     * 同一サイズのフォントはキャッシュされ、再利用される。
     *
     * @param size フォントサイズ（ピクセル）
     * @param color フォントの初期色（デフォルト: 白）
     * @return 日本語対応の BitmapFont
     */
    fun getFont(size: Int = 24, color: Color = Color.WHITE): BitmapFont {
        return fontCache.getOrPut(size) {
            ensureGenerator()
            val parameter = FreeTypeFontParameter().apply {
                this.size = size
                this.color = color
                this.characters = JAPANESE_CHARS
                // アンチエイリアス有効
                this.mono = false
                // ミップマップ設定（スケーリング品質向上）
                this.minFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
                this.magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
            }
            generator!!.generateFont(parameter).also {
                Gdx.app.log(TAG, "フォント生成完了: size=$size")
            }
        }
    }

    /**
     * FreeTypeFontGenerator を初期化する（遅延初期化）
     */
    private fun ensureGenerator() {
        if (generator == null) {
            generator = FreeTypeFontGenerator(Gdx.files.internal(FONT_PATH))
            Gdx.app.log(TAG, "FreeTypeFontGenerator 初期化完了: $FONT_PATH")
        }
    }

    /**
     * 全リソースを解放する
     *
     * アプリケーション終了時に呼び出すこと。
     */
    override fun dispose() {
        fontCache.values.forEach { it.dispose() }
        fontCache.clear()
        generator?.dispose()
        generator = null
        Gdx.app.log(TAG, "FontManager リソース解放完了")
    }

    private const val TAG = "FontManager"
}
