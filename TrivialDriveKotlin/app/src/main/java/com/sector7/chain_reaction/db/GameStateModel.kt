/*
 * Copyright (C) 2021 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sector7.chain_reaction.db

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

class GameStateModel(application: Application) {
    private val db: GameStateDatabase = Room.databaseBuilder(
        application, GameStateDatabase::class.java, "GameState.db"
    ).createFromAsset("database/initialgamestate.db").build()
    private val gameStateDao: GameStateDao = db.gameStateDao()
    val gasTankLevel = gameStateDao["gas"].distinctUntilChanged()
        .shareIn(CoroutineScope(Dispatchers.Main), SharingStarted.Lazily, 1)
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO

    suspend fun decrementGas(minLevel: Int) = withContext(defaultDispatcher) {
        gameStateDao.decrement("gas", minLevel)
    }

    suspend fun incrementGas(maxLevel: Int) = withContext(defaultDispatcher) {
        gameStateDao.increment("gas", maxLevel)
    }
}
