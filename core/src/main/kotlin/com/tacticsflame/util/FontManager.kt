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
 *
 * パフォーマンス最適化:
 * - ゲーム内で実際に使用する漢字のみをホワイトリストで指定（約550字）
 * - CJK全範囲（約21,000字）を含まないことでフォント生成時間・VRAM消費を大幅削減
 * - テクスチャサイズを 1024x1024 に最適化
 *
 * 新しい漢字が必要になった場合は KANJI_WHITELIST に追加すること。
 */
object FontManager : Disposable {

    /** フォントファイルのパス */
    private const val FONT_PATH = "fonts/NotoSansJP.ttf"

    /**
     * ゲーム内で使用する漢字のホワイトリスト
     *
     * JSONデータ・マップデータ・Kotlinソース内の全テキストから自動抽出した漢字（約550字）。
     * 新しいテキストを追加した際は、使用する漢字をここに追記すること。
     */
    private const val KANJI_WHITELIST =
        "一三上下不与両中丸主乗乱了予事二互交人今介仕他付代令以仮仲件任伏伝位低体何余作使例依侵係保個倍倒候値停側備傭像優" +
        "元先兜入全公共兵具内円再処出刀分切列初判別利到制削前剣副割力功加効勇動勝包化北十半危厚去参反収取受句可右号各合同" +
        "名向含周味呼命品員問営噂器回団困囲図固国圏圧在地均型城域基報場塞境壁士変外大天央失奪始威子字存守安完定実宮容寄対" +
        "専射将小少届属山川左差布帯帷常幅平幸序度座庫廃延廷式弓引弧張強当形影径待後従得御復循微心必応思悪情想意感態慎慮成" +
        "戦戻所扇手扱承技抜択押拡括持指挑挙捉掃排掛探接控推描提換援損携撃撤播操攻放敗敢数整敵文斧断新方既日旧昇明易映時景" +
        "暗更書替最有期未本村杖条来杯果枠査格案森検極構槍標権横橋機次止正武歩殊殺毎比気水汎決河油況法注流消済減渡測満準滅漢" +
        "潜点無照片物特状狙独猛獲率王現理環生用由画界留略番疎疾発登白的盗盟目直相盾省着短砦破確示秒移程種積空突立章端符第等" +
        "箇算管範築簡系約納素索紫累細終経結統継続維網総緑緒線編縦置羅群義考者背能脅脛脱自致色英落蓄行衛表被装補複要覆見規視" +
        "覧角解計記訪設試該詳認語説読調護象負費賊賢質赤走起超越足距跡路軍軽較輪辺込迂近返追退送逃透途通速連進遅遇運過道達遠" +
        "適遭遷選避還部配重量金針鉄鋼録鎖鎧長門閉開間関閲閾闇闘防限陣除険隊隔際隠隣集離難青非面革靴響順須領頭類風飛飾馬騎験高魔黄黒"

    /**
     * 生成に含める日本語文字セット
     *
     * ASCII + ひらがな + カタカナ + ゲーム使用漢字 + UI記号のみ。
     * CJK全範囲を含まないことで、フォント生成速度とVRAM消費を大幅に削減する。
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
            // 全角英数・半角カタカナ（よく使う記号のみ）
            append("\uFF01\uFF0C\uFF0E\uFF1A\uFF1B\uFF1F\uFF5E")
            append("\uFF10\uFF11\uFF12\uFF13\uFF14\uFF15\uFF16\uFF17\uFF18\uFF19")
            // ゲーム内で使用する漢字のみ（ホワイトリスト）
            append(KANJI_WHITELIST)
            // 画面UIで使用する特殊記号
            append("\u2014\u2015\u2190\u2191\u2192\u2193\u25B6\u25B7\u25C0\u25C1\u25CF\u25CB\u25A0\u25A1\u25B2\u25B3\u25BC\u25BD\u2713\u2717\u2605\u2606")
        }
    }

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
                // ホワイトリスト方式のため 1024x1024 で十分
                this.packer = com.badlogic.gdx.graphics.g2d.PixmapPacker(
                    1024, 1024, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888, 1, false
                )
                // テクスチャフィルタリング（スケーリング品質向上）
                this.minFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
                this.magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
            }
            generator!!.generateFont(parameter).also {
                Gdx.app.log(TAG, "フォント生成完了: size=$size, 文字数=${JAPANESE_CHARS.length}")
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
