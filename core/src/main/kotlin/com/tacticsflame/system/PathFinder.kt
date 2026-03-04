package com.tacticsflame.system

import com.tacticsflame.model.map.BattleMap
import com.tacticsflame.model.map.Position
import com.tacticsflame.model.map.TerrainType
import com.tacticsflame.model.unit.MoveType
import com.tacticsflame.model.unit.GameUnit
import java.util.PriorityQueue

/**
 * A*アルゴリズムによる経路探索システム
 */
class PathFinder {

    /**
     * ユニットの移動可能範囲を計算する
     *
     * @param unit 移動ユニット
     * @param startPos 開始座標
     * @param battleMap バトルマップ
     * @return 移動可能な座標のセット
     */
    fun getMovablePositions(unit: GameUnit, startPos: Position, battleMap: BattleMap): Set<Position> {
        val movable = mutableSetOf<Position>()
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
                if (moveCost < 0) continue // 通行不可

                val totalCost = currentCost + moveCost
                if (totalCost > unit.mov) continue

                // 他のユニットがいるマスは通過可(味方)だが、敵がいたら不可
                val unitOnTile = battleMap.getUnitAt(neighbor)
                if (unitOnTile != null && unitOnTile.faction != unit.faction) continue

                if (totalCost < (costMap[neighbor] ?: Int.MAX_VALUE)) {
                    costMap[neighbor] = totalCost
                    queue.add(neighbor to totalCost)

                    // 味方がいるマスは通過のみ可（停止不可）
                    if (unitOnTile == null) {
                        movable.add(neighbor)
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
     * @param unit 移動ユニット
     * @param start 開始座標
     * @param goal 目標座標
     * @param battleMap バトルマップ
     * @return 経路（座標リスト）、到達不可の場合は空リスト
     */
    fun findPath(unit: GameUnit, start: Position, goal: Position, battleMap: BattleMap): List<Position> {
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

                val tile = battleMap.getTile(neighbor) ?: continue
                val moveCost = getMoveCost(unit.unitClass.moveType, tile.terrainType)
                if (moveCost < 0) continue

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
