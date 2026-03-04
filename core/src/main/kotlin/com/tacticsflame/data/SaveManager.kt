package com.tacticsflame.data

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.JsonWriter
import com.tacticsflame.model.campaign.GameProgress
import com.tacticsflame.model.unit.*
import java.io.StringWriter

/**
 * ゲームのセーブ/ロード機能を管理するクラス
 *
 * LibGDX の Gdx.files.local() を使用してローカルストレージに JSON 形式で保存する。
 * Android では内部ストレージ、Desktop ではプロジェクトルートに保存される。
 */
object SaveManager {

    private const val TAG = "SaveManager"

    /** セーブファイル名 */
    private const val SAVE_FILE_NAME = "save_data.json"

    /** 一時セーブファイル名（アトミックセーブ用） */
    private const val SAVE_FILE_NAME_TMP = "save_data.json.tmp"

    /** セーブデータバージョン */
    private const val SAVE_VERSION = 1

    /**
     * ゲーム進行状態をローカルファイルに保存する
     *
     * @param gameProgress 保存するゲーム進行状態
     * @return 保存に成功した場合 true
     */
    fun save(gameProgress: GameProgress): Boolean {
        return try {
            val json = serializeToJson(gameProgress)
            // アトミックセーブ: 一時ファイルに書き込んでからリネーム（書き込み中クラッシュ時のデータ破損防止）
            val tmpFile = Gdx.files.local(SAVE_FILE_NAME_TMP)
            tmpFile.writeString(json, false)
            val file = Gdx.files.local(SAVE_FILE_NAME)
            // 一時ファイルを本番ファイルにリネーム（安全な上書き）
            val tmpJavaFile = tmpFile.file()
            val destJavaFile = file.file()
            if (destJavaFile.exists()) destJavaFile.delete()
            tmpJavaFile.renameTo(destJavaFile)
            Gdx.app.log(TAG, "セーブ完了: ${file.path()}")
            true
        } catch (e: Exception) {
            Gdx.app.error(TAG, "セーブ失敗: ${e.message}", e)
            false
        }
    }

    /**
     * ローカルファイルからゲーム進行状態を復元する
     *
     * @param gameProgress 復元先の GameProgress（initialize() 済みであること）
     * @return ロードに成功した場合 true
     */
    fun load(gameProgress: GameProgress): Boolean {
        return try {
            val file = Gdx.files.local(SAVE_FILE_NAME)
            if (!file.exists()) {
                Gdx.app.log(TAG, "セーブデータが見つかりません。新規ゲームです。")
                return false
            }
            val jsonStr = file.readString("UTF-8")
            val root = JsonReader().parse(jsonStr)
            deserializeFromJson(root, gameProgress)
            Gdx.app.log(TAG, "ロード完了")
            true
        } catch (e: Exception) {
            Gdx.app.error(TAG, "ロード失敗: ${e.message}", e)
            false
        }
    }

    /**
     * セーブデータが存在するかどうかを確認する
     *
     * @return セーブファイルが存在すれば true
     */
    fun hasSaveData(): Boolean {
        return try {
            Gdx.files.local(SAVE_FILE_NAME).exists()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * セーブデータを削除する（ニューゲーム用）
     *
     * @return 削除に成功した場合 true
     */
    fun deleteSaveData(): Boolean {
        return try {
            val file = Gdx.files.local(SAVE_FILE_NAME)
            if (file.exists()) {
                file.delete()
                Gdx.app.log(TAG, "セーブデータ削除完了")
            }
            true
        } catch (e: Exception) {
            Gdx.app.error(TAG, "セーブデータ削除失敗: ${e.message}", e)
            false
        }
    }

    // ==================== シリアライズ ====================

    /**
     * GameProgress を JSON 文字列に変換する
     *
     * @param gameProgress シリアライズ対象
     * @return JSON 文字列
     */
    internal fun serializeToJson(gameProgress: GameProgress): String {
        val stringWriter = StringWriter()
        val writer = JsonWriter(stringWriter)
        writer.setOutputType(JsonWriter.OutputType.json)

        writer.`object`()
        writer.set("version", SAVE_VERSION)

        // チャプター進行状態
        writer.`object`("chapters")
        for (chapter in gameProgress.chapters) {
            writer.`object`(chapter.id)
            writer.set("unlocked", chapter.unlocked)
            writer.set("completed", chapter.completed)
            writer.pop()
        }
        writer.pop()

        // パーティ情報
        writer.`object`("party")

        // ロスター（所持ユニット一覧）
        writer.array("roster")
        for (unit in gameProgress.party.roster) {
            writeUnit(writer, unit)
        }
        writer.pop()

        // 出撃メンバーIDリスト
        writer.array("deployedIds")
        for (id in gameProgress.party.deployedIds) {
            writer.value(id)
        }
        writer.pop()

        writer.pop() // party

        writer.pop() // root
        writer.close()

        return stringWriter.toString()
    }

    /**
     * 1ユニットの情報を JSON に書き出す
     *
     * @param writer JSON ライター
     * @param unit 書き出すユニット
     */
    private fun writeUnit(writer: JsonWriter, unit: GameUnit) {
        writer.`object`()
        writer.set("id", unit.id)
        writer.set("name", unit.name)
        writer.set("classId", unit.unitClass.id)
        writer.set("faction", unit.faction.name)
        writer.set("level", unit.level)
        writer.set("exp", unit.exp)
        writer.set("currentHp", unit.currentHp)
        writer.set("isLord", unit.isLord)
        writer.set("tactic", unit.tactic.name)

        // ステータス
        writer.`object`("stats")
        writeStats(writer, unit.stats)
        writer.pop()

        // 成長率
        writer.`object`("growthRate")
        writeGrowthRate(writer, unit.growthRate)
        writer.pop()

        // 武器リスト（予備武器）
        writer.array("weapons")
        for (weapon in unit.weapons) {
            writeWeapon(writer, weapon)
        }
        writer.pop()

        // 装備スロット: 右手
        val rh = unit.rightHand
        if (rh != null) {
            writer.`object`("rightHand")
            writeWeaponFields(writer, rh)
            writer.pop()
        }

        // 装備スロット: 左手
        val lh = unit.leftHand
        if (lh != null) {
            writer.`object`("leftHand")
            writeWeaponFields(writer, lh)
            writer.pop()
        }

        // 装備スロット: 防具1
        val a1 = unit.armorSlot1
        if (a1 != null) {
            writer.`object`("armorSlot1")
            writeArmor(writer, a1)
            writer.pop()
        }

        // 装備スロット: 防具2
        val a2 = unit.armorSlot2
        if (a2 != null) {
            writer.`object`("armorSlot2")
            writeArmor(writer, a2)
            writer.pop()
        }

        writer.pop()
    }

    /**
     * ステータスを JSON に書き出す
     */
    private fun writeStats(writer: JsonWriter, stats: Stats) {
        writer.set("hp", stats.hp)
        writer.set("str", stats.str)
        writer.set("mag", stats.mag)
        writer.set("skl", stats.skl)
        writer.set("spd", stats.spd)
        writer.set("lck", stats.lck)
        writer.set("def", stats.def)
        writer.set("res", stats.res)
    }

    /**
     * 成長率を JSON に書き出す
     */
    private fun writeGrowthRate(writer: JsonWriter, growthRate: GrowthRate) {
        writer.set("hp", growthRate.hp)
        writer.set("str", growthRate.str)
        writer.set("mag", growthRate.mag)
        writer.set("skl", growthRate.skl)
        writer.set("spd", growthRate.spd)
        writer.set("lck", growthRate.lck)
        writer.set("def", growthRate.def)
        writer.set("res", growthRate.res)
    }

    /**
     * 武器を JSON 配列要素として書き出す（object/pop 付き）
     */
    private fun writeWeapon(writer: JsonWriter, weapon: Weapon) {
        writer.`object`()
        writeWeaponFields(writer, weapon)
        writer.pop()
    }

    /**
     * 武器のフィールドを JSON に書き出す（object/pop なし）
     */
    private fun writeWeaponFields(writer: JsonWriter, weapon: Weapon) {
        writer.set("id", weapon.id)
        writer.set("name", weapon.name)
        writer.set("type", weapon.type.name)
        writer.set("might", weapon.might)
        writer.set("hit", weapon.hit)
        writer.set("critical", weapon.critical)
        writer.set("weight", weapon.weight)
        writer.set("minRange", weapon.minRange)
        writer.set("maxRange", weapon.maxRange)
    }

    /**
     * 防具を JSON に書き出す
     */
    private fun writeArmor(writer: JsonWriter, armor: Armor) {
        writer.set("id", armor.id)
        writer.set("name", armor.name)
        writer.set("type", armor.type.name)
        writer.set("defBonus", armor.defBonus)
        writer.set("resBonus", armor.resBonus)
        writer.set("weight", armor.weight)
    }

    // ==================== デシリアライズ ====================

    /**
     * JSON データから GameProgress を復元する
     *
     * @param root JSON ルートノード
     * @param gameProgress 復元先（initialize() で初期データがセットアップ済み）
     */
    internal fun deserializeFromJson(root: JsonValue, gameProgress: GameProgress) {
        val version = root.getInt("version", 1)
        Gdx.app.log(TAG, "セーブデータバージョン: $version")

        // バージョンマイグレーション（将来のバージョンアップ時にここで変換処理を追加）
        if (version > SAVE_VERSION) {
            Gdx.app.error(TAG, "セーブデータが現在のアプリより新しいバージョンです（データ: v$version、アプリ: v$SAVE_VERSION）")
            return
        }
        // v1 → v2 などのマイグレーションが必要な場合はここに追加
        // if (version < 2) { migrateV1toV2(root) }

        // チャプター進行状態の復元
        val chaptersNode = root.get("chapters")
        if (chaptersNode != null) {
            for (chapter in gameProgress.chapters) {
                val chapterNode = chaptersNode.get(chapter.id)
                if (chapterNode != null) {
                    chapter.unlocked = chapterNode.getBoolean("unlocked", chapter.unlocked)
                    chapter.completed = chapterNode.getBoolean("completed", chapter.completed)
                }
            }
        }

        // パーティ情報の復元
        val partyNode = root.get("party")
        if (partyNode != null) {
            restoreParty(partyNode, gameProgress)
        }
    }

    /**
     * パーティ情報を JSON から復元する
     *
     * @param partyNode パーティ JSON ノード
     * @param gameProgress 復元先
     */
    private fun restoreParty(partyNode: JsonValue, gameProgress: GameProgress) {
        val rosterNode = partyNode.get("roster")
        if (rosterNode != null) {
            // セーブデータからユニットを読み込み、既存ロスターを上書き
            val savedUnits = mutableListOf<GameUnit>()
            var unitNode = rosterNode.child
            while (unitNode != null) {
                val unit = readUnit(unitNode)
                if (unit != null) {
                    savedUnits.add(unit)
                }
                unitNode = unitNode.next
            }

            // ロスターを再構築
            if (savedUnits.isNotEmpty()) {
                gameProgress.party.clearRoster()
                gameProgress.party.addUnits(savedUnits)
            }
        }

        // 出撃メンバーの復元
        val deployedNode = partyNode.get("deployedIds")
        if (deployedNode != null) {
            val deployedIds = mutableListOf<String>()
            var idNode = deployedNode.child
            while (idNode != null) {
                deployedIds.add(idNode.asString())
                idNode = idNode.next
            }
            gameProgress.party.setDeployedUnits(deployedIds)
        }
    }

    /**
     * JSON からユニットを1体読み込む
     *
     * @param node ユニット JSON ノード
     * @return 復元した GameUnit（失敗時は null）
     */
    private fun readUnit(node: JsonValue): GameUnit? {
        return try {
            val id = node.getString("id")
            val name = node.getString("name")
            val classId = node.getString("classId")
            val factionName = node.getString("faction")
            val level = node.getInt("level", 1)
            val exp = node.getInt("exp", 0)
            val currentHp = node.getInt("currentHp", 0)
            val isLord = node.getBoolean("isLord", false)
            val tacticName = node.getString("tactic", UnitTactic.CHARGE.name)
            val tactic = try {
                UnitTactic.valueOf(tacticName)
            } catch (e: IllegalArgumentException) {
                UnitTactic.CHARGE
            }

            val unitClass = UnitClass.ALL[classId] ?: run {
                Gdx.app.error(TAG, "不明なクラスID: $classId")
                return null
            }
            val faction = try {
                Faction.valueOf(factionName)
            } catch (e: IllegalArgumentException) {
                Gdx.app.error(TAG, "不明な陣営: $factionName")
                Faction.PLAYER
            }

            val stats = readStats(node.get("stats"))
            val growthRate = readGrowthRate(node.get("growthRate"))
            val weapons = readWeapons(node.get("weapons"))

            val unit = GameUnit(
                id = id,
                name = name,
                unitClass = unitClass,
                faction = faction,
                level = level,
                exp = exp,
                stats = stats,
                growthRate = growthRate,
                weapons = weapons.toMutableList(),
                isLord = isLord
            )
            unit.setCurrentHp(currentHp)
            unit.tactic = tactic

            // 新しい装備スロットの復元
            val rightHandNode = node.get("rightHand")
            if (rightHandNode != null) {
                // 新フォーマット: スロット別に読み込み
                unit.rightHand = readWeapon(rightHandNode)

                val leftHandNode = node.get("leftHand")
                if (leftHandNode != null) {
                    unit.leftHand = readWeapon(leftHandNode)
                }

                val armorSlot1Node = node.get("armorSlot1")
                if (armorSlot1Node != null) {
                    unit.armorSlot1 = readArmor(armorSlot1Node)
                }

                val armorSlot2Node = node.get("armorSlot2")
                if (armorSlot2Node != null) {
                    unit.armorSlot2 = readArmor(armorSlot2Node)
                }
            } else {
                // 旧フォーマットからの移行: weapons[0] → rightHand
                if (unit.weapons.isNotEmpty()) {
                    unit.rightHand = unit.weapons.removeAt(0)
                }

                val armorNode = node.get("equippedArmor")
                if (armorNode != null) {
                    unit.armorSlot1 = readArmor(armorNode)
                }
            }

            unit
        } catch (e: Exception) {
            Gdx.app.error(TAG, "ユニット読み込み失敗: ${e.message}", e)
            null
        }
    }

    /**
     * JSON からステータスを読み込む
     */
    private fun readStats(node: JsonValue?): Stats {
        if (node == null) return Stats()
        return Stats(
            hp = node.getInt("hp", 0),
            str = node.getInt("str", 0),
            mag = node.getInt("mag", 0),
            skl = node.getInt("skl", 0),
            spd = node.getInt("spd", 0),
            lck = node.getInt("lck", 0),
            def = node.getInt("def", 0),
            res = node.getInt("res", 0)
        )
    }

    /**
     * JSON から成長率を読み込む
     */
    private fun readGrowthRate(node: JsonValue?): GrowthRate {
        if (node == null) return GrowthRate()
        return GrowthRate(
            hp = node.getInt("hp", 0),
            str = node.getInt("str", 0),
            mag = node.getInt("mag", 0),
            skl = node.getInt("skl", 0),
            spd = node.getInt("spd", 0),
            lck = node.getInt("lck", 0),
            def = node.getInt("def", 0),
            res = node.getInt("res", 0)
        )
    }

    /**
     * JSON から武器リストを読み込む
     */
    private fun readWeapons(node: JsonValue?): List<Weapon> {
        if (node == null) return emptyList()
        val weapons = mutableListOf<Weapon>()
        var weaponNode = node.child
        while (weaponNode != null) {
            weapons.add(readWeapon(weaponNode))
            weaponNode = weaponNode.next
        }
        return weapons
    }

    /**
     * JSON から武器を1つ読み込む
     */
    private fun readWeapon(node: JsonValue): Weapon {
        val typeName = node.getString("type", "SWORD")
        val weaponType = try {
            WeaponType.valueOf(typeName)
        } catch (e: IllegalArgumentException) {
            Gdx.app.error(TAG, "不明な武器タイプ: $typeName、SWORD で代替")
            WeaponType.SWORD
        }
        return Weapon(
            id = node.getString("id", "unknown"),
            name = node.getString("name", "???"),
            type = weaponType,
            might = node.getInt("might", 0),
            hit = node.getInt("hit", 0),
            critical = node.getInt("critical", 0),
            weight = node.getInt("weight", 0),
            minRange = node.getInt("minRange", 1),
            maxRange = node.getInt("maxRange", 1)
        )
    }

    /**
     * JSON から防具を1つ読み込む
     *
     * @param node 防具 JSON ノード
     * @return 復元した Armor
     */
    private fun readArmor(node: JsonValue): Armor {
        val typeName = node.getString("type", "LIGHT_ARMOR")
        val armorType = try {
            ArmorType.valueOf(typeName)
        } catch (e: IllegalArgumentException) {
            Gdx.app.error(TAG, "不明な防具タイプ: $typeName、LIGHT_ARMOR で代替")
            ArmorType.LIGHT_ARMOR
        }
        return Armor(
            id = node.getString("id", "unknown"),
            name = node.getString("name", "???"),
            type = armorType,
            defBonus = node.getInt("defBonus", 0),
            resBonus = node.getInt("resBonus", 0),
            weight = node.getInt("weight", 0)
        )
    }
}
