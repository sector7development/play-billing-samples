package com.sector7.chain_reaction

import android.app.Activity
import android.util.Log
import com.sector7.chain_reaction.billing.BillingDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class TrivialDriveRepository(
    private val billingDataSource: BillingDataSource, defaultScope: CoroutineScope
) {
    val messages = MutableSharedFlow<Int>()
    val billingLifecycleObserver get() = billingDataSource

    init {
        defaultScope.launch {
            try {
                billingDataSource.newPurchases.collect { skuList ->
                    skuList.forEach {
                        when (it) {
                            SKU_TABLET_BOARDS -> messages.emit(R.string.message_premium)
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.d("Repository", "Collection complete")
            }
        }
    }

    fun buySku(activity: Activity, sku: String) {
        billingDataSource.launchBillingFlow(activity, sku)
    }

    fun isPurchased(sku: String) = billingDataSource.isPurchased(sku)
    fun canPurchase(sku: String) = billingDataSource.canPurchase(sku)
    fun getSkuTitle(sku: String) = billingDataSource.getSkuTitle(sku)
    fun getSkuPrice(sku: String) = billingDataSource.getSkuPrice(sku)
    fun getSkuDescription(sku: String) = billingDataSource.getSkuDescription(sku)

    companion object {
        const val SKU_TABLET_BOARDS = "tablet_boards"
    }
}
