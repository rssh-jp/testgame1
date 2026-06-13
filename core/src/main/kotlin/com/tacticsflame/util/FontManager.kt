package com.tacticsflame.util

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
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
 * - 基本はホワイトリストを使用し、起動時に assets/data から不足漢字を自動収集
 * - incremental 生成で、初期セット外の文字も描画時に安全に追加
 * - CJK全範囲（約21,000字）を固定で含めないことで初期生成コストを抑制
 */
object FontManager : Disposable {

    /** フォントファイルのパス */
    private const val FONT_PATH = "fonts/NotoSansJP.ttf"

    /** フォント文字収集対象のデータディレクトリ */
    private const val DATA_DIR_PATH = "data"

    /**
     * ゲーム内で使用する漢字のホワイトリスト
     *
    * 既存画面で頻出する漢字をベースとして保持する。
    * 追加データ由来の漢字は assets/data から自動収集して補完される。
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
        val collectedDataKanji = collectKanjiFromDataAssets()
        val baseChars = buildString {
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
            // assets/data から収集した漢字を追加（新規データ増加時の欠字を防止）
            append(collectedDataKanji)
            // 画面UIで使用する特殊記号
            append("\u2014\u2015\u2190\u2191\u2192\u2193\u25B6\u25B7\u25C0\u25C1\u25CF\u25CB\u25A0\u25A1\u25B2\u25B3\u25BC\u25BD\u2713\u2717\u2605\u2606")
        }
        val uniqueChars = baseChars.toSortedSet().joinToString(separator = "")
        Gdx.app.log(TAG, "フォント文字セット構築: base=${KANJI_WHITELIST.length}, data=${collectedDataKanji.length}, total=${uniqueChars.length}")
        uniqueChars
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
                // 初期セットにない文字も描画時に動的生成して欠字を防止
                this.incremental = true
                // アンチエイリアス有効
                this.mono = false
                // incremental=true のOOMリスクを下げるため、サイズに応じてパッカーを抑制する
                val packerSize = resolvePackerSize(size)
                this.packer = com.badlogic.gdx.graphics.g2d.PixmapPacker(
                    packerSize,
                    packerSize,
                    com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888,
                    1,
                    false
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
     * フォントサイズに応じて安全側の PixmapPacker サイズを返す
     */
    private fun resolvePackerSize(fontSize: Int): Int {
        return if (fontSize <= 32) 1024 else 1536
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
     * assets/data 配下の JSON から漢字を収集する
     *
     * 収集に失敗した場合は空文字を返し、既存ホワイトリストのみで継続する。
     */
    private fun collectKanjiFromDataAssets(): String {
        return runCatching {
            val dataDir = Gdx.files.internal(DATA_DIR_PATH)
            if (!dataDir.exists() || !dataDir.isDirectory) {
                Gdx.app.log(TAG, "データ文字収集スキップ: $DATA_DIR_PATH が存在しません")
                return ""
            }

            val kanji = buildString {
                collectKanjiRecursive(dataDir, this)
            }
            kanji.toSortedSet().joinToString(separator = "")
        }.getOrElse { error ->
            Gdx.app.error(TAG, "データ文字収集に失敗したためホワイトリストへフォールバック", error)
            ""
        }
    }

    /**
     * ファイルツリーを再帰走査し、対象JSONの漢字のみ抽出する
     */
    private fun collectKanjiRecursive(fileHandle: FileHandle, builder: StringBuilder) {
        if (fileHandle.isDirectory) {
            fileHandle.list().forEach { child ->
                collectKanjiRecursive(child, builder)
            }
            return
        }

        if (fileHandle.extension().lowercase() != "json") {
            return
        }

        val text = runCatching {
            fileHandle.readString("UTF-8")
        }.getOrElse { error ->
            Gdx.app.error(TAG, "文字収集対象ファイルの読み込み失敗: ${fileHandle.path()}", error)
            return
        }

        text.forEach { char ->
            if (char in '\u4E00'..'\u9FFF') {
                builder.append(char)
            }
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
