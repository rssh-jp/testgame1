package com.tacticsflame.model.unit

/**
 * ユニットごとに設定可能な作戦（AIの行動方針）
 *
 * 部隊編成画面でプレイヤーユニットごとに選択でき、
 * バトル中のAI自動行動の方針を決定する。
 *
 * @property displayName UI表示用の作戦命令名
 * @property description 作戦の説明文
 */
enum class UnitTactic(val displayName: String, val description: String) {
    /** 一番近い敵に向かって移動し、攻撃を試みる */
    CHARGE("勇猛果敢に戦え", "一番近い敵に向かって移動し、攻撃を試みる"),

    /** 敵から攻撃されない位置で待機し、自分のターンで攻撃できるようにする */
    CAUTIOUS("後の先を狙え", "敵の攻撃圏外から攻撃を狙う"),

    /** 味方と一緒に敵を攻撃する */
    SUPPORT("味方を援護しろ", "味方が狙っている敵を優先して攻撃する"),

    /** 味方のHP回復を優先する（杖装備時のみ有効） */
    HEAL("味方を回復しろ", "HPが減った味方の回復を優先する"),

    /** 敵から逃げるように行動する */
    FLEE("逃げまどえ", "敵から離れるように移動する");

    /**
     * 次の作戦を返す（サイクル切り替え用）
     *
     * @return 次の作戦（最後の場合は先頭に戻る）
     */
    fun next(): UnitTactic {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}
