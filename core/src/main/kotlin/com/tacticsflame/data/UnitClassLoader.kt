package com.tacticsflame.data

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.tacticsflame.core.AssetPaths
import com.tacticsflame.model.unit.GrowthRate
import com.tacticsflame.model.unit.MoveType
import com.tacticsflame.model.unit.Stats
import com.tacticsflame.model.unit.UnitClass
import com.tacticsflame.model.unit.WeaponType

/**
 * ユニットクラス（兵種）データを classes.json から読み込むローダー
 *
 * 初回アクセス時にファイルを読み込み、以降はキャッシュを返す。
 * MapLoader の武器/防具ロードと同パターン。
 *
 * 注意: このオブジェクトはスレッドセーフではない。
 * LibGDX のメインスレッド（OpenGL スレッド）からのみ呼び出すこと。
 *
 * JSONフォーマット例（data/classes.json）:
 * ```json
 * [
 *   {
 *     "id": "lord",
 *     "name": "ロード",
 *     "moveType": "INFANTRY",
 *     "baseMov": 5,
 *     "usableWeapons": ["SWORD"],
 *     "canDualWield": true,
 *     "dualWieldPenalty": 2,
 *     "classGrowthRate": {
 *       "hp": 1.60, "str": 0.40, "mag": 0.10, "skl": 0.35,
 *       "spd": 0.30, "lck": 0.25, "def": 0.25, "res": 0.15
 *     }
 *   },
 *   ...
 * ]
 * ```
 */
object UnitClassLoader {

    /** キャッシュ済みクラスデータ（id → UnitClass） */
    private var classData: Map<String, UnitClass>? = null

    private const val TAG = "UnitClassLoader"

    /**
     * classes.json を読み込み、全クラスデータを返す
     *
     * ロード済みならキャッシュを返す。ファイル未発見やパースエラー時は
     * ログを出力し空マップを返す（呼び出し元がデフォルト値にフォールバックする設計）。
     *
     * @return クラスID → UnitClass のマップ
     */
    fun loadAll(): Map<String, UnitClass> {
        classData?.let { return it }

        return try {
            val fileHandle = Gdx.files.internal(AssetPaths.CLASS_DATA)
            if (!fileHandle.exists()) {
                Gdx.app.error(TAG, "クラスデータファイルが見つかりません: ${AssetPaths.CLASS_DATA}")
                // ファイル未発見時はキャッシュしない（次回呼び出しでリトライ可能）
                return emptyMap()
            }

            val jsonText = fileHandle.readString("UTF-8")
            val jsonArray = JsonReader().parse(jsonText)
            val result = parseClassArray(jsonArray)

            classData = result
            Gdx.app.log(TAG, "クラスデータ読み込み完了: ${result.size}件")
            result
        } catch (e: Exception) {
            Gdx.app.error(TAG, "クラスデータ読み込みエラー", e)
            // エラー時はキャッシュしない（次回呼び出しでリトライ可能）
            emptyMap()
        }
    }

    /**
     * 指定IDのクラスを返す
     *
     * 未ロード時は [loadAll] を呼んでからキャッシュを検索する。
     *
     * @param id クラスID
     * @return 該当する [UnitClass]、見つからなければ null
     */
    fun getById(id: String): UnitClass? {
        return loadAll()[id]
    }

    /**
     * キャッシュをクリアし、次回アクセス時に再読み込みを強制する
     */
    fun reload() {
        classData = null
    }

    /**
     * ロード済みかどうか
     */
    val isLoaded: Boolean
        get() = classData != null

    /**
     * JSON文字列から直接ロードする（テスト用）
     *
     * Gdx 依存なしで使えるよう、[JsonReader] のみでパースを行う。
     * キャッシュには保存しない。
     *
     * @param jsonString JSON配列形式の文字列
     * @return クラスID → UnitClass のマップ
     */
    fun loadFromString(jsonString: String): Map<String, UnitClass> {
        val jsonArray = JsonReader().parse(jsonString)
        return parseClassArray(jsonArray)
    }

    // ==================== 内部パースメソッド ====================

    /**
     * JSON配列をパースし、id → UnitClass のマップを返す
     *
     * 各要素のパースに失敗した場合はスキップしてログ出力する。
     *
     * @param jsonArray クラス定義のJSON配列
     * @return パース成功したクラスのマップ
     */
    private fun parseClassArray(jsonArray: JsonValue): Map<String, UnitClass> {
        val result = mutableMapOf<String, UnitClass>()

        for (i in 0 until jsonArray.size) {
            val entry = jsonArray[i]
            try {
                val unitClass = parseUnitClass(entry)
                if (unitClass != null) {
                    result[unitClass.id] = unitClass
                }
            } catch (e: Exception) {
                val entryId = entry.getString("id", "(不明)")
                logWarning("クラスエントリのパースに失敗しました [index=$i, id=$entryId]: ${e.message}")
            }
        }

        return result
    }

    /**
     * 単一のJSON要素から [UnitClass] をパースする
     *
     * id / name / moveType が取得できない場合は null を返す。
     *
     * @param json クラス定義の単一JSONオブジェクト
     * @return パースした [UnitClass]、必須フィールド欠如時は null
     */
    private fun parseUnitClass(json: JsonValue): UnitClass? {
        // 必須フィールド: id
        val id = json.getString("id", null)
        if (id == null) {
            logWarning("クラスエントリに id がありません、スキップします")
            return null
        }

        // 必須フィールド: name
        val name = json.getString("name", null)
        if (name == null) {
            logWarning("クラスエントリに name がありません [id=$id]、スキップします")
            return null
        }

        // 必須フィールド: moveType
        val moveTypeStr = json.getString("moveType", null)
        if (moveTypeStr == null) {
            logWarning("クラスエントリに moveType がありません [id=$id]、スキップします")
            return null
        }
        val moveType = try {
            MoveType.valueOf(moveTypeStr)
        } catch (e: IllegalArgumentException) {
            logWarning("不正な moveType: '$moveTypeStr' [id=$id]、スキップします")
            return null
        }

        // 必須フィールド: baseMov
        val baseMov = json.getInt("baseMov", -1)
        if (baseMov < 0) {
            logWarning("クラスエントリに baseMov がありません [id=$id]、スキップします")
            return null
        }

        // 装備可能武器タイプ（配列）
        val usableWeapons = mutableListOf<WeaponType>()
        val weaponsArray = json.get("usableWeapons")
        if (weaponsArray != null && weaponsArray.isArray) {
            for (j in 0 until weaponsArray.size) {
                val weaponStr = weaponsArray[j].asString()
                try {
                    usableWeapons.add(WeaponType.valueOf(weaponStr))
                } catch (e: IllegalArgumentException) {
                    logWarning("不正な WeaponType: '$weaponStr' [id=$id]、該当武器をスキップします")
                }
            }
        }

        // オプションフィールド
        val canDualWield = json.getBoolean("canDualWield", false)
        val dualWieldPenalty = json.getInt("dualWieldPenalty", 0)

        // クラス成長率
        val classGrowthRate = parseGrowthRate(json.get("classGrowthRate"))

        // baseStats（オプション: 未指定時は全ゼロのデフォルト値）
        val baseStats = parseStats(json.get("baseStats"))
        if (baseStats.hp <= 0f) {
            logWarning("baseStats.hp が 0 以下です [id=$id]。classes.json に baseStats が正しく設定されているか確認してください")
        }

        return UnitClass(
            id = id,
            name = name,
            moveType = moveType,
            baseMov = baseMov,
            usableWeapons = usableWeapons,
            baseStats = baseStats,
            canDualWield = canDualWield,
            dualWieldPenalty = dualWieldPenalty,
            classGrowthRate = classGrowthRate
        )
    }

    /**
     * JSON オブジェクトから [Stats] をパースする
     *
     * null が渡された場合やフィールドが存在しない場合はデフォルト値 (0.0f) を使用する。
     *
     * @param json ステータスを表すJSONオブジェクト（null許容）
     * @return パースした [Stats]
     */
    private fun parseStats(json: JsonValue?): Stats {
        if (json == null) return Stats()

        return Stats(
            hp = json.getFloat("hp", 0.0f),
            str = json.getFloat("str", 0.0f),
            mag = json.getFloat("mag", 0.0f),
            skl = json.getFloat("skl", 0.0f),
            spd = json.getFloat("spd", 0.0f),
            lck = json.getFloat("lck", 0.0f),
            def = json.getFloat("def", 0.0f),
            res = json.getFloat("res", 0.0f)
        )
    }

    /**
     * JSON オブジェクトから [GrowthRate] をパースする
     *
     * null が渡された場合やフィールドが存在しない場合はデフォルト値 (0.0f) を使用する。
     *
     * @param json 成長率を表すJSONオブジェクト（null許容）
     * @return パースした [GrowthRate]
     */
    private fun parseGrowthRate(json: JsonValue?): GrowthRate {
        if (json == null) return GrowthRate()

        return GrowthRate(
            hp = json.getFloat("hp", 0.0f),
            str = json.getFloat("str", 0.0f),
            mag = json.getFloat("mag", 0.0f),
            skl = json.getFloat("skl", 0.0f),
            spd = json.getFloat("spd", 0.0f),
            lck = json.getFloat("lck", 0.0f),
            def = json.getFloat("def", 0.0f),
            res = json.getFloat("res", 0.0f)
        )
    }

    /**
     * 警告ログを出力する（Gdx が利用可能な場合のみ）
     *
     * @param message ログメッセージ
     */
    private fun logWarning(message: String) {
        try {
            Gdx.app?.log(TAG, message)
        } catch (_: Exception) {
            // Gdx 未初期化時（テスト環境など）は無視
        }
    }
}
