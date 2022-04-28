package com.sector7.chain_reaction.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameStateDao {
    @Query("SELECT `value` FROM GameState WHERE `key` = :key LIMIT 1")
    operator fun get(key: String): Flow<Int>

    @Query("REPLACE INTO GameState VALUES(:key,:value)")
    fun put(key: String, value: Int)

    @Query("UPDATE GameState SET `value`=`value`-1 WHERE `key`=:key AND `value` > :minValue")
    fun decrement(key: String, minValue: Int): Int

    @Query("UPDATE GameState SET `value`=`value`+1 WHERE `key`=:key AND `value` < :maxValue")
    fun increment(key: String, maxValue: Int): Int
}
