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

package com.sector7.chain_reaction

import android.app.Activity
import android.util.Log
import androidx.lifecycle.LifecycleObserver
import com.sector7.chain_reaction.billing.BillingDataSource
import com.sector7.chain_reaction.db.GameStateModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TrivialDriveRepository(
    private val billingDataSource: BillingDataSource,
    private val gameStateModel: GameStateModel,
    private val defaultScope: CoroutineScope
) {
    private val gameMessages: MutableSharedFlow<Int> = MutableSharedFlow()

    private fun postMessagesFromBillingFlow() {
        defaultScope.launch {
            try {
                billingDataSource.getNewPurchases().collect { skuList ->
                    for (sku in skuList) {
                        when (sku) {
                            SKU_GAS -> gameMessages.emit(R.string.message_more_gas_acquired)
                            SKU_PREMIUM -> gameMessages.emit(R.string.message_premium)
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.d(TAG, "Collection complete")
            }
            Log.d(TAG, "Collection Coroutine Scope Exited")
        }
    }

    suspend fun drive() {
        when (val gasTankLevel = gasTankLevel().first()) {
            GAS_TANK_INFINITE -> sendMessage(R.string.message_infinite_drive)
            GAS_TANK_MIN -> sendMessage(R.string.message_out_of_gas)
            else -> {
                val newGasLevel = gasTankLevel - gameStateModel.decrementGas(GAS_TANK_MIN)
                Log.d(TAG, "Old Gas Level: $gasTankLevel New Gas Level: $newGasLevel")
                if (newGasLevel == GAS_TANK_MIN) {
                    sendMessage(R.string.message_out_of_gas)
                } else {
                    sendMessage(R.string.message_you_drove)
                }
            }
        }
    }

    fun buySku(activity: Activity, sku: String) {
        billingDataSource.launchBillingFlow(activity, sku)
    }

    fun isPurchased(sku: String): Flow<Boolean> {
        return billingDataSource.isPurchased(sku)
    }

    fun canPurchase(sku: String): Flow<Boolean> {
        return when (sku) {
            SKU_GAS -> {
                billingDataSource.canPurchase(sku)
                    .combine(gasTankLevel()) { canPurchase, gasTankLevel ->
                        canPurchase && gasTankLevel < GAS_TANK_MAX
                    }
            }
            else -> billingDataSource.canPurchase(sku)
        }
    }

    fun gasTankLevel() = gameStateModel.gasTankLevel

    suspend fun refreshPurchases() {
        billingDataSource.refreshPurchases()
    }

    val billingLifecycleObserver: LifecycleObserver
        get() = billingDataSource

    fun getSkuTitle(sku: String): Flow<String> {
        return billingDataSource.getSkuTitle(sku)
    }

    fun getSkuPrice(sku: String): Flow<String> {
        return billingDataSource.getSkuPrice(sku)
    }

    fun getSkuDescription(sku: String): Flow<String> {
        return billingDataSource.getSkuDescription(sku)
    }

    val messages: Flow<Int>
        get() = gameMessages

    suspend fun sendMessage(stringId: Int) {
        gameMessages.emit(stringId)
    }

    val billingFlowInProcess: Flow<Boolean>
        get() = billingDataSource.getBillingFlowInProcess()

    companion object {
        // Source for all constants
        const val GAS_TANK_MIN = 0
        const val GAS_TANK_MAX = 4
        const val GAS_TANK_INFINITE = 5

        const val SKU_PREMIUM = "premium"
        const val SKU_GAS = "gas"

        val TAG = TrivialDriveRepository::class.simpleName
        val INAPP_SKUS = arrayOf(SKU_PREMIUM, SKU_GAS, "tablet_boards")
    }

    init {
        postMessagesFromBillingFlow()
    }
}
