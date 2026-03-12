package com.tacticsflame.system

import com.tacticsflame.model.map.BattleMap
import com.tacticsflame.model.map.Position
import com.tacticsflame.model.map.TerrainType
import com.tacticsflame.model.unit.MoveType
import com.tacticsflame.model.unit.GameUnit
import java.util.PriorityQueue

/**
 * ダイクストラ / A* による経路探索・移動コスト計算システム
 */
class PathFinder {

    /** バウンディングボックスのマージン（タイル数） */
    private companion object {
        const val BOUNDING_BOX_MARGIN = 5
    }

    /**
     * ユニットの移動可能範囲を計算する
     *
     * @param unit 移動ユニット
     * @param startPos 開始座標
     * @param battleMap バトルマップ
     * @return 移動可能な座標のセット
     */
    fun getMovablePositions(unit: GameUnit, startPos: Position, battleMap: BattleMap): Set<Position> {
        return getMovablePositionsWithCost(unit, startPos, battleMap).keys
    }

    /**
     * ユニットの移動可能範囲をコストマップ付きで計算する
     *
     * @param unit 移動ユニット
     * @param startPos 開始座標
     * @param battleMap バトルマップ
     * @return 移動可能座標とそこまでの移動コストのマップ
     */
    fun getMovablePositionsWithCost(unit: GameUnit, startPos: Position, battleMap: BattleMap): Map<Position, Int> {
        val movable = mutableMapOf<Position, Int>()
        val costMap = mutableMapOf<Position, Int>()
        val queue = PriorityQueue<Pair<Position, Int>>(compareBy { it.second })

        queue.add(startPos to 0)
        costMap[startPos] = 0

        while (queue.isNotEmpty()) {
            val (current, currentCost) = queue.poll()

            for (neighbor in current.neighbors()) {
                if (!battleMap.isInBounds(neighbor.x, neighbor.y)) continue

                val tile = battleMap.getTile(neighbor) ?: continue
                val moveCost = getMoveCost(unit.unitClass.moveType, tile.terrainType)
                if (moveCost < 0) continue

                val totalCost = currentCost + moveCost
                if (totalCost > unit.mov) continue

                val unitOnTile = battleMap.getUnitAt(neighbor)
                if (unitOnTile != null && unitOnTile.faction != unit.faction) continue

                if (totalCost < (costMap[neighbor] ?: Int.MAX_VALUE)) {
                    costMap[neighbor] = totalCost
                    queue.add(neighbor to totalCost)

                    if (unitOnTile == null) {
                        movable[neighbor] = totalCost
                    }
                }
            }
        }

        return movable
    }

    /**
     * 攻撃可能範囲を計算する
     *
     * 武器未装備（素手）の場合は射程1で計算する。
     *
     * @param unit 攻撃ユニット
     * @param movablePositions 移動可能座標
     * @param battleMap バトルマップ
     * @return 攻撃可能な座標のセット
     */
    fun getAttackablePositions(
        unit: GameUnit,
        movablePositions: Set<Position>,
        battleMap: BattleMap
    ): Set<Position> {
        val minRange = unit.attackMinRange()
        val maxRange = unit.attackMaxRange()
        val attackable = mutableSetOf<Position>()

        // 現在位置も含める
        val allPositions = movablePositions + (battleMap.getUnitPosition(unit) ?: return emptySet())

        for (pos in allPositions) {
            for (range in minRange..maxRange) {
                for (target in getPositionsAtRange(pos, range)) {
                    if (battleMap.isInBounds(target.x, target.y) && target !in movablePositions) {
                        attackable.add(target)
                    }
                }
            }
        }

        return attackable
    }

    /**
     * A*アルゴリズムで最短経路を探索する
     *
     * 探索範囲を全ユニットのバウンディングボックス + マージンに制限し、
     * パフォーマンスを向上させる。
     *
     * @param unit 移動ユニット
     * @param start 開始座標
     * @param goal 目標座標
     * @param battleMap バトルマップ
     * @return 経路（座標リスト）、到達不可の場合は空リスト
     */
    fun findPath(unit: GameUnit, start: Position, goal: Position, battleMap: BattleMap): List<Position> {
        // バウンディングボックスを計算して探索範囲を制限
        val bounds = calculateBoundingBox(battleMap, start, goal)

        val openSet = PriorityQueue<PathNode>(compareBy { it.fCost })
        val closedSet = mutableSetOf<Position>()
        val cameFrom = mutableMapOf<Position, Position>()
        val gScore = mutableMapOf<Position, Int>()

        gScore[start] = 0
        openSet.add(PathNode(start, 0, start.manhattanDistance(goal)))

        while (openSet.isNotEmpty()) {
            val current = openSet.poll()

            if (current.position == goal) {
                return reconstructPath(cameFrom, goal)
            }

            closedSet.add(current.position)

            for (neighbor in current.position.neighbors()) {
                if (neighbor in closedSet) continue
                if (!battleMap.isInBounds(neighbor.x, neighbor.y)) continue

                // バウンディングボックス外のノードはスキップ
                if (!bounds.contains(neighbor)) continue

                val tile = battleMap.getTile(neighbor) ?: continue
                val moveCost = getMoveCost(unit.unitClass.moveType, tile.terrainType)
                if (moveCost < 0) continue

                // 敵ユニットがいるマスは通過不可（すり抜け防止）
                val unitOnTile = battleMap.getUnitAt(neighbor)
                if (unitOnTile != null && unitOnTile.faction != unit.faction) continue

                val tentativeG = (gScore[current.position] ?: Int.MAX_VALUE) + moveCost

                if (tentativeG < (gScore[neighbor] ?: Int.MAX_VALUE)) {
                    cameFrom[neighbor] = current.position
                    gScore[neighbor] = tentativeG
                    openSet.add(PathNode(neighbor, tentativeG, neighbor.manhattanDistance(goal)))
                }
            }
        }

        return emptyList() // 到達不可
    }

    /**
     * 指定座標間の経路コストを計算する
     *
     * ダイクストラ法によりユニットの移動タイプを考慮した実パスコストを返す。
     * 到達不可の場合は Int.MAX_VALUE を返す。
     *
     * @param unit 移動ユニット
     * @param from 開始座標
     * @param to 目標座標
     * @param battleMap バトルマップ
     * @return 経路コスト（到達不可の場合 Int.MAX_VALUE）
     */
    fun getPathCostTo(unit: GameUnit, from: Position, to: Position, battleMap: BattleMap): Int {
        if (from == to) return 0

        val bounds = calculateBoundingBox(battleMap, from, to)
        val costMap = mutableMapOf<Position, Int>()
        val queue = PriorityQueue<Pair<Position, Int>>(compareBy { it.second })

        costMap[from] = 0
        queue.add(from to 0)

        while (queue.isNotEmpty()) {
            val (current, currentCost) = queue.poll()

            if (current == to) return currentCost

            // 既により良いコストで到達済みならスキップ
            if (currentCost > (costMap[current] ?: Int.MAX_VALUE)) continue

            for (neighbor in current.neighbors()) {
                if (!battleMap.isInBounds(neighbor.x, neighbor.y)) continue
                if (!bounds.contains(neighbor)) continue

                val tile = battleMap.getTile(neighbor) ?: continue
                val moveCost = getMoveCost(unit.unitClass.moveType, tile.terrainType)
                if (moveCost < 0) continue

                val unitOnTile = battleMap.getUnitAt(neighbor)
                if (unitOnTile != null && unitOnTile.faction != unit.faction) continue

                val totalCost = currentCost + moveCost
                if (totalCost < (costMap[neighbor] ?: Int.MAX_VALUE)) {
                    costMap[neighbor] = totalCost
                    queue.add(neighbor to totalCost)
                }
            }
        }

        return Int.MAX_VALUE // 到達不可
    }

    /**
     * 指定座標を起点に全方向へダイクストラ法でコストマップを構築する
     *
     * AIの接近先選定で、目的地からの逆方向コストマップとして使用する。
     * 探索範囲はバウンディングボックスで制限される。
     * 注: 接近方向の指標として使用するため、敵ユニットのブロックは意図的に無視する。
     * 実際の移動先は movablePositions（ブロック考慮済み）から選択される。
     *
     * @param unit 移動ユニット（移動タイプ判定用）
     * @param origin 起点座標
     * @param battleMap バトルマップ
     * @return 各座標への移動コストマップ（到達不可座標は含まれない）
     */
    fun buildCostMapFrom(unit: GameUnit, origin: Position, battleMap: BattleMap): Map<Position, Int> {
        val bounds = calculateBoundingBox(battleMap, origin, origin)
        val costMap = mutableMapOf<Position, Int>()
        val queue = PriorityQueue<Pair<Position, Int>>(compareBy { it.second })

        costMap[origin] = 0
        queue.add(origin to 0)

        while (queue.isNotEmpty()) {
            val (current, currentCost) = queue.poll()
            if (currentCost > (costMap[current] ?: Int.MAX_VALUE)) continue

            for (neighbor in current.neighbors()) {
                if (!battleMap.isInBounds(neighbor.x, neighbor.y)) continue
                if (!bounds.contains(neighbor)) continue

                val tile = battleMap.getTile(neighbor) ?: continue
                val moveCost = getMoveCost(unit.unitClass.moveType, tile.terrainType)
                if (moveCost < 0) continue

                val totalCost = currentCost + moveCost
                if (totalCost < (costMap[neighbor] ?: Int.MAX_VALUE)) {
                    costMap[neighbor] = totalCost
                    queue.add(neighbor to totalCost)
                }
            }
        }

        return costMap
    }

    /**
     * 複数起点からの同時ダイクストラでコストマップを構築する
     *
     * 複数の攻撃可能位置への最短コストを一度のダイクストラで計算する。
     * AIの接近先選定で、到達不可の敵に対して攻撃射程内の位置へ
     * 接近するために使用する。
     * 注: 接近方向の指標として使用するため、敵ユニットのブロックは意図的に無視する。
     * 実際の移動先は movablePositions（ブロック考慮済み）から選択される。
     *
     * @param unit 移動ユニット（移動タイプ判定用）
     * @param origins 起点座標リスト
     * @param battleMap バトルマップ
     * @return 各座標から最も近い起点への移動コストマップ
     */
    fun buildCostMapFromMultiple(unit: GameUnit, origins: List<Position>, battleMap: BattleMap): Map<Position, Int> {
        if (origins.isEmpty()) return emptyMap()

        // 全起点のmin/maxを算出
        var minX = origins.first().x
        var maxX = origins.first().x
        var minY = origins.first().y
        var maxY = origins.first().y
        for (pos in origins) {
            if (pos.x < minX) minX = pos.x
            if (pos.x > maxX) maxX = pos.x
            if (pos.y < minY) minY = pos.y
            if (pos.y > maxY) maxY = pos.y
        }

        // バウンディングボックスは全起点を含むように計算
        val bounds = calculateBoundingBox(battleMap, Position(minX, minY), Position(maxX, maxY))

        val costMap = mutableMapOf<Position, Int>()
        val queue = PriorityQueue<Pair<Position, Int>>(compareBy { it.second })

        // 全起点をコスト0で初期化
        for (origin in origins) {
            costMap[origin] = 0
            queue.add(origin to 0)
        }

        while (queue.isNotEmpty()) {
            val (current, currentCost) = queue.poll()
            if (currentCost > (costMap[current] ?: Int.MAX_VALUE)) continue

            for (neighbor in current.neighbors()) {
                if (!battleMap.isInBounds(neighbor.x, neighbor.y)) continue
                if (!bounds.contains(neighbor)) continue

                val tile = battleMap.getTile(neighbor) ?: continue
                val moveCost = getMoveCost(unit.unitClass.moveType, tile.terrainType)
                if (moveCost < 0) continue

                val totalCost = currentCost + moveCost
                if (totalCost < (costMap[neighbor] ?: Int.MAX_VALUE)) {
                    costMap[neighbor] = totalCost
                    queue.add(neighbor to totalCost)
                }
            }
        }

        return costMap
    }

    /**
     * ユニットがその座標に立てるか（地形が通行可能か）判定する
     *
     * @param unit 移動ユニット
     * @param pos 判定座標
     * @param battleMap バトルマップ
     * @return 通行可能なら true
     */
    fun isPassableFor(unit: GameUnit, pos: Position, battleMap: BattleMap): Boolean {
        val tile = battleMap.getTile(pos) ?: return false
        return getMoveCost(unit.unitClass.moveType, tile.terrainType) >= 0
    }

    /**
     * 移動タイプに応じた地形の移動コストを返す
     *
     * @param moveType 移動タイプ
     * @param terrain 地形タイプ
     * @return 移動コスト（-1は通行不可）
     */
    private fun getMoveCost(moveType: MoveType, terrain: TerrainType): Int {
        // 飛行ユニットは全地形を移動コスト1で通過可能（壁を除く）
        if (moveType == MoveType.FLYING) {
            return if (terrain == TerrainType.WALL) -1 else 1
        }

        return when {
            !terrain.isPassable() -> -1
            // 騎馬は森・山のコストが高い
            moveType == MoveType.CAVALRY && terrain == TerrainType.FOREST -> 3
            moveType == MoveType.CAVALRY && terrain == TerrainType.MOUNTAIN -> -1
            else -> terrain.moveCost
        }
    }

    /**
     * 指定座標から指定距離の全座標を取得する
     *
     * @param center 中心座標
     * @param range 距離
     * @return 該当座標のリスト
     */
    private fun getPositionsAtRange(center: Position, range: Int): List<Position> {
        val positions = mutableListOf<Position>()
        for (dx in -range..range) {
            val dy = range - Math.abs(dx)
            positions.add(Position(center.x + dx, center.y + dy))
            if (dy != 0) {
                positions.add(Position(center.x + dx, center.y - dy))
            }
        }
        return positions
    }

    /**
     * 全ユニット位置からバウンディングボックスを計算する
     *
     * start と goal を必ず含むようにボックスを拡張し、
     * 各方向にマージンを加えてマップ範囲内にクランプする。
     *
     * @param battleMap バトルマップ
     * @param start 開始座標
     * @param goal 目標座標
     * @return バウンディングボックス
     */
    private fun calculateBoundingBox(battleMap: BattleMap, start: Position, goal: Position): BoundingBox {
        val allUnits = battleMap.getAllUnits()

        // 全ユニット座標 + start + goal から min/max を算出
        var minX = minOf(start.x, goal.x)
        var maxX = maxOf(start.x, goal.x)
        var minY = minOf(start.y, goal.y)
        var maxY = maxOf(start.y, goal.y)

        for ((pos, _) in allUnits) {
            if (pos.x < minX) minX = pos.x
            if (pos.x > maxX) maxX = pos.x
            if (pos.y < minY) minY = pos.y
            if (pos.y > maxY) maxY = pos.y
        }

        // マージンを追加し、マップ範囲にクランプ
        return BoundingBox(
            minX = maxOf(0, minX - BOUNDING_BOX_MARGIN),
            maxX = minOf(battleMap.width - 1, maxX + BOUNDING_BOX_MARGIN),
            minY = maxOf(0, minY - BOUNDING_BOX_MARGIN),
            maxY = minOf(battleMap.height - 1, maxY + BOUNDING_BOX_MARGIN)
        )
    }

    /**
     * 探索範囲を制限するバウンディングボックス
     */
    private data class BoundingBox(
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int
    ) {
        /** 座標がボックス内に含まれるか判定する */
        fun contains(pos: Position): Boolean =
            pos.x in minX..maxX && pos.y in minY..maxY
    }

    /**
     * 経路を復元する
     */
    private fun reconstructPath(cameFrom: Map<Position, Position>, goal: Position): List<Position> {
        val path = mutableListOf(goal)
        var current = goal
        while (cameFrom.containsKey(current)) {
            current = cameFrom[current]!!
            path.add(0, current)
        }
        return path
    }

    /**
     * 経路探索用のノード
     */
    private data class PathNode(
        val position: Position,
        val gCost: Int,
        val hCost: Int
    ) {
        val fCost: Int get() = gCost + hCost
    }
}
