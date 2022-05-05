package com.sector7.chain_reaction

import android.app.Activity
import android.util.Log
import androidx.lifecycle.LifecycleObserver
import com.sector7.chain_reaction.billing.BillingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class BillingRepository(private val billingManager: BillingManager, defaultScope: CoroutineScope) {
    val messages = MutableSharedFlow<Int>()
    val billingLifecycleObserver get(): LifecycleObserver = billingManager

    init {
        defaultScope.launch {
            try {
                billingManager.newPurchases.collect { skuList ->
                    skuList.forEach {
                        when (it) {
                            "tablet_boards" -> messages.emit(R.string.message_premium)
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.d("Repository", "Collection complete")
            }
        }
    }

    fun buySku(activity: Activity, sku: String) {
        billingManager.launchBillingFlow(activity, sku)
    }
}
