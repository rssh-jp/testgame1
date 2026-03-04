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
     * ひらがな・カタカナ・CJK統合漢字・記号を包括的に含む。
     * 手動で漢字を列挙する方式ではなく、Unicode範囲指定で全漢字を網羅する。
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
            // CJK統合漢字（全範囲: 日本語の常用漢字・人名漢字をすべて含む）
            for (c in '\u4E00'..'\u9FFF') append(c)
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
                // CJK全漢字を含むためテクスチャページを大きめに確保
                this.packer = com.badlogic.gdx.graphics.g2d.PixmapPacker(
                    2048, 2048, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888, 1, false
                )
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
