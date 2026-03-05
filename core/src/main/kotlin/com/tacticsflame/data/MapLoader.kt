package com.tacticsflame.data

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.tacticsflame.core.AssetPaths
import com.tacticsflame.model.map.*
import com.tacticsflame.model.unit.*
import com.tacticsflame.system.VictoryChecker

/**
 * マップJSONファイルを読み込み、BattleMap・敵ユニット・スポーン位置などを構築するローダー
 *
 * JSONフォーマット（例: chapter_1.json）:
 * ```json
 * {
 *   "id": "chapter_1",
 *   "name": "第1章",
 *   "width": 15,
 *   "height": 10,
 *   "terrain": [[0,0,1,...], ...],
 *   "terrainKey": {"0":"PLAIN","1":"FOREST",...},
 *   "playerSpawns": [{"x":2,"y":2}, ...],
 *   "enemies": [{
 *     "id":"enemy_01", "classId":"axeFighter", "name":"山賊A",
 *     "level":1, "x":11, "y":3, "ai":"aggressive",
 *     "weaponId":"ironAxe",
 *     "stats": {"hp":18,"str":6,...},
 *     "isLord": false
 *   }, ...],
 *   "victoryCondition": {"type":"DEFEAT_ALL"},
 *   "defeatCondition": {"type":"LORD_DEFEATED"}
 * }
 * ```
 */
class MapLoader {

    companion object {
        private const val TAG = "MapLoader"

        /** 武器マスターデータ（weapons.json から読み込み） */
        private var weaponMasterData: Map<String, WeaponData>? = null

        /** 防具マスターデータ（armors.json から読み込み） */
        private var armorMasterData: Map<String, ArmorData>? = null
    }

    /**
     * 武器のマスターデータ（JSONパース用中間データ）
     */
    private data class WeaponData(
        val id: String,
        val name: String,
        val type: WeaponType,
        val might: Int,
        val hit: Int,
        val critical: Int = 0,
        val weight: Int = 0,
        val minRange: Int = 1,
        val maxRange: Int = 1,
        val healPower: Int = 0
    ) {
        /**
         * Weapon インスタンスを生成する
         */
        fun toWeapon(): Weapon = Weapon(
            id = id,
            name = name,
            type = type,
            might = might,
            hit = hit,
            critical = critical,
            weight = weight,
            minRange = minRange,
            maxRange = maxRange,
            healPower = healPower
        )
    }

    /**
     * 防具のマスターデータ（JSONパース用中間データ）
     */
    private data class ArmorData(
        val id: String,
        val name: String,
        val type: ArmorType,
        val defBonus: Int,
        val resBonus: Int,
        val weight: Int
    ) {
        /**
         * Armor インスタンスを生成する
         */
        fun toArmor(): Armor = Armor(
            id = id,
            name = name,
            type = type,
            defBonus = defBonus,
            resBonus = resBonus,
            weight = weight
        )
    }

    /**
     * マップ読み込み結果を格納するデータクラス
     *
     * @property battleMap 構築されたバトルマップ（ユニット未配置）
     * @property playerSpawns プレイヤーの出撃位置リスト
     * @property enemies 敵ユニットと配置位置のペアリスト
     * @property victoryConditionType 勝利条件タイプ
     */
    data class MapLoadResult(
        val battleMap: BattleMap,
        val playerSpawns: List<Position>,
        val enemies: List<Pair<GameUnit, Position>>,
        val victoryConditionType: VictoryChecker.VictoryConditionType
    )

    /**
     * マップJSONファイルを読み込み、MapLoadResult を返す
     *
     * @param mapFileName マップファイル名（例: "chapter_1.json"）
     * @param partyAverageLevel パーティの平均レベル（ランダム敵生成用、デフォルト0＝未指定）
     * @return 読み込み結果。読み込み失敗時は null
     */
    fun loadMap(mapFileName: String, partyAverageLevel: Int = 0): MapLoadResult? {
        return try {
            ensureWeaponDataLoaded()
            ensureArmorDataLoaded()

            val filePath = "${AssetPaths.MAP_DIR}$mapFileName"
            val fileHandle = Gdx.files.internal(filePath)
            if (!fileHandle.exists()) {
                Gdx.app.error(TAG, "マップファイルが見つかりません: $filePath")
                return null
            }

            val jsonText = fileHandle.readString("UTF-8")
            val json = JsonReader().parse(jsonText)

            val battleMap = parseBattleMap(json)
            val playerSpawns = parsePlayerSpawns(json)

            // ランダム敵生成フラグが true の場合、敵を動的生成する
            val isRandomEnemies = json.getBoolean("randomEnemies", false)
            val enemies = if (isRandomEnemies && partyAverageLevel > 0) {
                generateRandomEnemies(battleMap, playerSpawns, partyAverageLevel)
            } else {
                parseEnemies(json)
            }

            val victoryConditionType = parseVictoryCondition(json)

            Gdx.app.log(TAG, "マップ読み込み完了: $mapFileName " +
                "(${battleMap.width}x${battleMap.height}, スポーン: ${playerSpawns.size}, 敵: ${enemies.size}" +
                if (isRandomEnemies) ", ランダム敵Lv.$partyAverageLevel)" else ")")

            MapLoadResult(battleMap, playerSpawns, enemies, victoryConditionType)
        } catch (e: Exception) {
            Gdx.app.error(TAG, "マップ読み込みエラー: $mapFileName", e)
            null
        }
    }

    // ==================== 武器マスターデータ読み込み ====================

    /**
     * 武器マスターデータがまだ読み込まれていなければ読み込む
     */
    private fun ensureWeaponDataLoaded() {
        if (weaponMasterData != null) return

        try {
            val fileHandle = Gdx.files.internal(AssetPaths.WEAPON_DATA)
            if (!fileHandle.exists()) {
                Gdx.app.error(TAG, "武器データファイルが見つかりません: ${AssetPaths.WEAPON_DATA}")
                weaponMasterData = emptyMap()
                return
            }

            val jsonText = fileHandle.readString("UTF-8")
            val jsonArray = JsonReader().parse(jsonText)
            val weapons = mutableMapOf<String, WeaponData>()

            for (i in 0 until jsonArray.size) {
                val w = jsonArray[i]
                val id = w.getString("id")
                val weaponType = parseWeaponType(w.getString("type"))
                weapons[id] = WeaponData(
                    id = id,
                    name = w.getString("name"),
                    type = weaponType,
                    might = w.getInt("might"),
                    hit = w.getInt("hit"),
                    critical = w.getInt("critical", 0),
                    weight = w.getInt("weight", 0),
                    minRange = w.getInt("minRange", 1),
                    maxRange = w.getInt("maxRange", 1),
                    healPower = w.getInt("healPower", 0)
                )
            }

            weaponMasterData = weapons
            Gdx.app.log(TAG, "武器マスターデータ読み込み完了: ${weapons.size}件")
        } catch (e: Exception) {
            Gdx.app.error(TAG, "武器データ読み込みエラー", e)
            weaponMasterData = emptyMap()
        }
    }

    // ==================== 防具マスターデータ読み込み ====================

    /**
     * 防具マスターデータがまだ読み込まれていなければ読み込む
     */
    private fun ensureArmorDataLoaded() {
        if (armorMasterData != null) return

        try {
            val fileHandle = Gdx.files.internal(AssetPaths.ARMOR_DATA)
            if (!fileHandle.exists()) {
                Gdx.app.log(TAG, "防具データファイルが見つかりません: ${AssetPaths.ARMOR_DATA}（省略）")
                armorMasterData = emptyMap()
                return
            }

            val jsonText = fileHandle.readString("UTF-8")
            val jsonArray = JsonReader().parse(jsonText)
            val armors = mutableMapOf<String, ArmorData>()

            for (i in 0 until jsonArray.size) {
                val a = jsonArray[i]
                val id = a.getString("id")
                val armorType = parseArmorType(a.getString("type"))
                armors[id] = ArmorData(
                    id = id,
                    name = a.getString("name"),
                    type = armorType,
                    defBonus = a.getInt("defBonus", 0),
                    resBonus = a.getInt("resBonus", 0),
                    weight = a.getInt("weight", 0)
                )
            }

            armorMasterData = armors
            Gdx.app.log(TAG, "防具マスターデータ読み込み完了: ${armors.size}件")
        } catch (e: Exception) {
            Gdx.app.error(TAG, "防具データ読み込みエラー", e)
            armorMasterData = emptyMap()
        }
    }

    // ==================== マップパース ====================

    /**
     * JSONからBattleMapを構築する
     */
    private fun parseBattleMap(json: JsonValue): BattleMap {
        val id = json.getString("id")
        val name = json.getString("name")
        val width = json.getInt("width")
        val height = json.getInt("height")

        // terrainKey のパース（数値 → TerrainType名のマッピング）
        val terrainKey = mutableMapOf<Int, TerrainType>()
        val terrainKeyJson = json.get("terrainKey")
        if (terrainKeyJson != null) {
            var entry = terrainKeyJson.child
            while (entry != null) {
                val key = entry.name.toIntOrNull()
                val terrainType = parseTerrainType(entry.asString())
                if (key != null && terrainType != null) {
                    terrainKey[key] = terrainType
                }
                entry = entry.next
            }
        }

        // terrain 2D配列のパース
        val terrainArray = json.get("terrain")
        val tiles = Array(height) { y ->
            Array(width) { x ->
                val terrainId = if (y < terrainArray.size && x < terrainArray[y].size) {
                    terrainArray[y][x].asInt()
                } else {
                    0 // デフォルト: PLAIN
                }
                val terrainType = terrainKey[terrainId] ?: TerrainType.PLAIN
                Tile(Position(x, y), terrainType)
            }
        }

        return BattleMap(id, name, width, height, tiles)
    }

    /**
     * JSONからプレイヤーのスポーン位置リストをパースする
     */
    private fun parsePlayerSpawns(json: JsonValue): List<Position> {
        val spawnsJson = json.get("playerSpawns") ?: return emptyList()
        val spawns = mutableListOf<Position>()

        for (i in 0 until spawnsJson.size) {
            val spawn = spawnsJson[i]
            spawns.add(Position(spawn.getInt("x"), spawn.getInt("y")))
        }

        return spawns
    }

    /**
     * JSONから敵ユニットリストをパースする
     */
    private fun parseEnemies(json: JsonValue): List<Pair<GameUnit, Position>> {
        val enemiesJson = json.get("enemies") ?: return emptyList()
        val enemies = mutableListOf<Pair<GameUnit, Position>>()

        for (i in 0 until enemiesJson.size) {
            val e = enemiesJson[i]
            val enemy = parseEnemyUnit(e)
            if (enemy != null) {
                val pos = Position(e.getInt("x"), e.getInt("y"))
                enemies.add(enemy to pos)
            }
        }

        return enemies
    }

    /**
     * JSONから敵ユニット1体をパースする
     */
    private fun parseEnemyUnit(json: JsonValue): GameUnit? {
        val id = json.getString("id")
        val classId = json.getString("classId")
        val name = json.getString("name")
        val level = json.getInt("level", 1)
        val weaponId = json.getString("weaponId", null)
        val aiPatternStr = json.getString("ai", "aggressive")
        val isLord = json.getBoolean("isLord", false)

        val unitClass = UnitClass.ALL[classId]
        if (unitClass == null) {
            Gdx.app.error(TAG, "不明なクラスID: $classId (ユニット: $name)")
            return null
        }

        // statsのパース（JSONに含まれていなければデフォルトステータスを使用）
        val stats = parseStats(json.get("stats"))

        val unit = GameUnit(
            id = id,
            name = name,
            unitClass = unitClass,
            faction = Faction.ENEMY,
            level = level,
            stats = stats,
            growthRate = GrowthRate(), // 敵は成長率なし
            isLord = isLord
        )

        // 武器の装備
        if (weaponId != null) {
            val weapon = createWeapon(weaponId, id)
            if (weapon != null) {
                unit.rightHand = weapon
            }
        }

        // 防具の装備
        val armorId = json.getString("armorId", null)
        if (armorId != null) {
            val armor = createArmor(armorId, id)
            if (armor != null) {
                unit.armorSlot1 = armor
            }
        }

        return unit
    }

    /**
     * JSONからステータスをパースする
     */
    private fun parseStats(statsJson: JsonValue?): Stats {
        if (statsJson == null) {
            return Stats(hp = 18f, str = 5f, mag = 0f, skl = 3f, spd = 4f, lck = 1f, def = 3f, res = 0f)
        }
        return Stats(
            hp = statsJson.getFloat("hp", 18f),
            str = statsJson.getFloat("str", 5f),
            mag = statsJson.getFloat("mag", 0f),
            skl = statsJson.getFloat("skl", 3f),
            spd = statsJson.getFloat("spd", 4f),
            lck = statsJson.getFloat("lck", 1f),
            def = statsJson.getFloat("def", 3f),
            res = statsJson.getFloat("res", 0f)
        )
    }

    /**
     * 武器IDからWeaponインスタンスを生成する
     *
     * マスターデータに存在する場合はそこから生成し、
     * 存在しない場合はデフォルト武器を返す。
     *
     * @param weaponId 武器ID
     * @param unitId 装備するユニットIDnit（ログ用）
     * @return 生成されたWeapon。生成失敗時はnull
     */
    private fun createWeapon(weaponId: String, unitId: String): Weapon? {
        val masterData = weaponMasterData ?: return createFallbackWeapon(weaponId, unitId)

        val weaponData = masterData[weaponId]
        if (weaponData != null) {
            return weaponData.toWeapon()
        }

        Gdx.app.log(TAG, "武器マスターに未登録: $weaponId (ユニット: $unitId)。フォールバック生成。")
        return createFallbackWeapon(weaponId, unitId)
    }

    /**
     * マスターデータに存在しない武器IDに対するフォールバック武器を生成する
     */
    private fun createFallbackWeapon(weaponId: String, unitId: String): Weapon {
        // 武器IDから推測してデフォルト武器を生成
        val type = when {
            weaponId.contains("sword", ignoreCase = true) -> WeaponType.SWORD
            weaponId.contains("lance", ignoreCase = true) -> WeaponType.LANCE
            weaponId.contains("axe", ignoreCase = true) -> WeaponType.AXE
            weaponId.contains("bow", ignoreCase = true) -> WeaponType.BOW
            weaponId.contains("fire", ignoreCase = true) || weaponId.contains("magic", ignoreCase = true) -> WeaponType.MAGIC
            weaponId.contains("heal", ignoreCase = true) || weaponId.contains("staff", ignoreCase = true) -> WeaponType.STAFF
            else -> WeaponType.SWORD
        }

        val (might, hit, weight, minRange, maxRange) = when (type) {
            WeaponType.SWORD -> listOf(5, 90, 3, 1, 1)
            WeaponType.LANCE -> listOf(7, 80, 5, 1, 1)
            WeaponType.AXE -> listOf(8, 75, 6, 1, 1)
            WeaponType.BOW -> listOf(6, 85, 3, 2, 2)
            WeaponType.MAGIC -> listOf(5, 90, 2, 1, 2)
            WeaponType.STAFF -> listOf(0, 100, 1, 1, 1)
        }

        Gdx.app.log(TAG, "フォールバック武器生成: $weaponId → ${type.name} (ユニット: $unitId)")
        val healPower = if (type == WeaponType.STAFF) 10 else 0
        return Weapon(
            id = weaponId,
            name = weaponId,
            type = type,
            might = might,
            hit = hit,
            weight = weight,
            minRange = minRange,
            maxRange = maxRange,
            healPower = healPower
        )
    }

    /**
     * 防具IDからArmorインスタンスを生成する
     *
     * マスターデータに存在する場合はそこから生成し、
     * 存在しない場合はnullを返す。
     *
     * @param armorId 防具ID
     * @param unitId 装備するユニットID（ログ用）
     * @return 生成されたArmor。生成失敗時はnull
     */
    private fun createArmor(armorId: String, unitId: String): Armor? {
        val masterData = armorMasterData ?: return null

        val armorData = masterData[armorId]
        if (armorData != null) {
            return armorData.toArmor()
        }

        Gdx.app.log(TAG, "防具マスターに未登録: $armorId (ユニット: $unitId)")
        return null
    }

    // ==================== ランダム敵生成 ====================

    /**
     * ランダムマップ用に敵ユニットを動的生成する
     *
     * パーティ平均レベルに応じた敵を4〜6体、マップ右側のランダム位置に配置する。
     * 敵のステータスはベースステータス + レベルに応じた成長分で計算される。
     *
     * @param battleMap バトルマップ
     * @param playerSpawns プレイヤースポーン位置（敵と重複しないよう除外する）
     * @param averageLevel パーティの平均レベル
     * @return 敵ユニットと配置位置のペアリスト
     */
    private fun generateRandomEnemies(
        battleMap: BattleMap,
        playerSpawns: List<Position>,
        averageLevel: Int
    ): List<Pair<GameUnit, Position>> {
        val random = java.util.Random()
        val enemies = mutableListOf<Pair<GameUnit, Position>>()

        // 敵の数: 4〜6体
        val enemyCount = 4 + random.nextInt(3)

        // 敵クラスのプール（武器ID付き）
        val enemyTemplates = listOf(
            Triple(UnitClass.SWORD_FIGHTER, "ironSword", "剣士"),
            Triple(UnitClass.LANCER, "ironLance", "槍兵"),
            Triple(UnitClass.AXE_FIGHTER, "ironAxe", "斧戦士"),
            Triple(UnitClass.ARCHER, "ironBow", "弓兵"),
            Triple(UnitClass.MAGE, "fire", "魔道士")
        )

        // プレイヤースポーンと被らない位置を確保するためのセット
        val usedPositions = playerSpawns.toMutableSet()

        for (i in 0 until enemyCount) {
            val template = enemyTemplates[random.nextInt(enemyTemplates.size)]
            val unitClass = template.first
            val weaponId = template.second
            val nameBase = template.third

            // レベルを平均レベル ± 1 の範囲でランダム（最低1）
            val level = maxOf(1, averageLevel + random.nextInt(3) - 1)

            // ベースステータスにレベル分を上乗せ
            val stats = generateStatsForLevel(level, random)

            val unit = GameUnit(
                id = "random_enemy_${i + 1}",
                name = "${nameBase}${('A'.code + i).toChar()}",
                unitClass = unitClass,
                faction = Faction.ENEMY,
                level = level,
                stats = stats,
                growthRate = GrowthRate()
            )

            // 武器を装備
            val weapon = createWeapon(weaponId, unit.id)
            if (weapon != null) {
                unit.rightHand = weapon
            }

            // マップ右半分のランダム位置に配置
            val pos = findRandomEnemyPosition(battleMap, usedPositions, random)
            if (pos != null) {
                usedPositions.add(pos)
                enemies.add(unit to pos)
            }
        }

        Gdx.app.log(TAG, "ランダム敵生成完了: ${enemies.size}体 (平均Lv.$averageLevel)")
        return enemies
    }

    /**
     * 指定レベルに応じた敵ステータスを生成する
     *
     * Lv.1のベースステータスに、レベルごとに固定成長値を加算する。
     * 確定成長システム（FFT方式）に基づく決定論的な計算。
     *
     * @param level 敵のレベル
     * @param random 乱数生成器（互換性のため引数に残すが未使用）
     * @return 生成されたステータス
     */
    private fun generateStatsForLevel(level: Int, random: java.util.Random): Stats {
        // Lv.1 ベースステータス
        var hp = 18f; var str = 5f; var mag = 0f; var skl = 3f
        var spd = 4f; var lck = 1f; var def = 3f; var res = 0f

        // 敵用デフォルト成長値（確定加算）
        val levelsGrown = level - 1
        hp += 0.60f * levelsGrown
        str += 0.40f * levelsGrown
        mag += 0.20f * levelsGrown
        skl += 0.35f * levelsGrown
        spd += 0.20f * levelsGrown  // SPDは全ユニット共通 0.20
        lck += 0.25f * levelsGrown
        def += 0.30f * levelsGrown
        res += 0.20f * levelsGrown

        return Stats(hp = hp, str = str, mag = mag, skl = skl, spd = spd, lck = lck, def = def, res = res)
    }

    /**
     * マップ右半分から、使用済み位置と重複しないランダム位置を探す
     *
     * @param battleMap バトルマップ
     * @param usedPositions 既に使用されている位置
     * @param random 乱数生成器
     * @return 配置可能な位置（見つからなければnull）
     */
    private fun findRandomEnemyPosition(
        battleMap: BattleMap,
        usedPositions: Set<Position>,
        random: java.util.Random
    ): Position? {
        // マップ右半分の平地を候補にする
        val candidates = mutableListOf<Position>()
        val halfWidth = battleMap.width / 2
        for (y in 0 until battleMap.height) {
            for (x in halfWidth until battleMap.width) {
                val pos = Position(x, y)
                val tile = battleMap.getTile(x, y)
                if (pos !in usedPositions && tile != null &&
                    tile.terrainType != com.tacticsflame.model.map.TerrainType.MOUNTAIN &&
                    tile.terrainType != com.tacticsflame.model.map.TerrainType.WALL
                ) {
                    candidates.add(pos)
                }
            }
        }
        if (candidates.isEmpty()) return null
        return candidates[random.nextInt(candidates.size)]
    }

    // ==================== 条件パース ====================

    /**
     * JSONから勝利条件をパースする
     */
    private fun parseVictoryCondition(json: JsonValue): VictoryChecker.VictoryConditionType {
        val conditionJson = json.get("victoryCondition") ?: return VictoryChecker.VictoryConditionType.DEFEAT_ALL
        val type = conditionJson.getString("type", "DEFEAT_ALL")
        return when (type.uppercase()) {
            "DEFEAT_ALL" -> VictoryChecker.VictoryConditionType.DEFEAT_ALL
            "DEFEAT_BOSS" -> VictoryChecker.VictoryConditionType.DEFEAT_BOSS
            "SURVIVE_TURNS" -> VictoryChecker.VictoryConditionType.SURVIVE_TURNS
            "REACH_POINT" -> VictoryChecker.VictoryConditionType.REACH_POINT
            else -> {
                Gdx.app.log(TAG, "不明な勝利条件: $type → DEFEAT_ALL にフォールバック")
                VictoryChecker.VictoryConditionType.DEFEAT_ALL
            }
        }
    }

    // ==================== ユーティリティ ====================

    /**
     * 文字列からTerrainTypeを解決する
     */
    private fun parseTerrainType(name: String): TerrainType? {
        return try {
            TerrainType.valueOf(name.uppercase())
        } catch (e: IllegalArgumentException) {
            Gdx.app.error(TAG, "不明な地形タイプ: $name")
            null
        }
    }

    /**
     * 文字列からWeaponTypeを解決する
     */
    private fun parseWeaponType(name: String): WeaponType {
        return try {
            WeaponType.valueOf(name.uppercase())
        } catch (e: IllegalArgumentException) {
            Gdx.app.error(TAG, "不明な武器タイプ: $name → SWORD にフォールバック")
            WeaponType.SWORD
        }
    }

    /**
     * 文字列からArmorTypeを解決する
     */
    private fun parseArmorType(name: String): ArmorType {
        return try {
            ArmorType.valueOf(name.uppercase())
        } catch (e: IllegalArgumentException) {
            Gdx.app.error(TAG, "不明な防具タイプ: $name → LIGHT_ARMOR にフォールバック")
            ArmorType.LIGHT_ARMOR
        }
    }
}
