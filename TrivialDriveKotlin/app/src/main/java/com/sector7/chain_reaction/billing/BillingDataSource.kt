package com.sector7.chain_reaction.billing

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.android.billingclient.api.*
import com.sector7.chain_reaction.TrivialDriveRepository.Companion.SKU_TABLET_BOARDS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.min

private const val reconnectDelay = 1_000L // 1 second (
private const val maxReconnectDelay = 1_000L * 60L * 15L // 15 minutes
private const val skuRequeryDelay = 1_000L * 60L * 60L * 4L // 4 hours

class BillingDataSource private constructor(
    application: Application, private val defaultScope: CoroutineScope
) : LifecycleObserver {
    private val billingClient: BillingClient

    private val skus = listOf(SKU_TABLET_BOARDS)
    private var reconnectTimer = reconnectDelay // ms

    // when was the last successful SkuDetailsResponse?
    private var skuDetailsResponseTime = -skuRequeryDelay

    private enum class PurchaseState { Unowned, Pending, Purchased, Acknowledged }

    private val skuStateMap = skus.associateWith { MutableStateFlow(PurchaseState.Unowned) }

    private val skuDetailsMap = skus.associateWith {
        MutableStateFlow<SkuDetails?>(null).also {
            it.subscriptionCount.map { count -> count > 0 }.distinctUntilChanged()
                .onEach { isActive ->
                    if (isActive && (SystemClock.elapsedRealtime() - skuDetailsResponseTime > skuRequeryDelay)) {
                        skuDetailsResponseTime = SystemClock.elapsedRealtime()
                        querySkuDetailsAsync()
                    }
                }.launchIn(defaultScope)
        }
    }

    // Observables that are used to communicate state.
    private val _newPurchases = MutableSharedFlow<List<String>>(extraBufferCapacity = 1)
    private val billingFlowInProcess = MutableStateFlow(false)

    val newPurchases get() = _newPurchases.asSharedFlow()

    fun isPurchased(sku: String) = skuStateMap[sku]!!.map { it == PurchaseState.Acknowledged }

    fun canPurchase(sku: String) =
        skuStateMap[sku]!!.combine(skuDetailsMap[sku]!!) { skuState, skuDetails -> skuState == PurchaseState.Unowned && skuDetails != null }

    fun getSkuTitle(sku: String) =
        skuDetailsMap[sku]!!.mapNotNull { skuDetails -> skuDetails?.title }

    fun getSkuPrice(sku: String) =
        skuDetailsMap[sku]!!.mapNotNull { skuDetails -> skuDetails?.price }

    fun getSkuDescription(sku: String) =
        skuDetailsMap[sku]!!.mapNotNull { skuDetails -> skuDetails?.description }

    private fun onSkuDetailsResponse(result: BillingResult, skuDetailsList: List<SkuDetails>) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            skuDetailsList.forEach { skuDetailsMap[it.sku]?.value = it }
            skuDetailsResponseTime = SystemClock.elapsedRealtime()
        } else skuDetailsResponseTime = -skuRequeryDelay
    }

    private suspend fun querySkuDetailsAsync() {
        if (!skus.isNullOrEmpty()) {
            billingClient.querySkuDetails(
                SkuDetailsParams.newBuilder().setType(BillingClient.SkuType.INAPP).setSkusList(skus)
                    .build()
            ).let { (result, skuDetails) -> onSkuDetailsResponse(result, skuDetails!!) }
        }
    }

    private suspend fun refreshPurchases() {
        billingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP).let { (result, purchases) ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchaseList(purchases, skus)
            }
        }
    }

    private fun setSkuStateFromPurchase(purchase: Purchase) {
        purchase.skus.forEach {
            skuStateMap[it]?.value = when (purchase.purchaseState) {
                Purchase.PurchaseState.PENDING -> PurchaseState.Pending
                Purchase.PurchaseState.UNSPECIFIED_STATE -> PurchaseState.Unowned
                Purchase.PurchaseState.PURCHASED -> if (purchase.isAcknowledged) PurchaseState.Acknowledged else PurchaseState.Purchased
                else -> throw Exception("Purchase in unknown state")
            }
        }
    }

    private fun setSkuState(sku: String, state: PurchaseState) {
        skuStateMap[sku]?.value = state
    }

    private fun processPurchaseList(purchases: List<Purchase>, toUpdate: List<String>?) {
        val updatedSkus = HashSet<String>()
        purchases.forEach { purchase ->
            updatedSkus.addAll(purchase.skus.filterNotNull())
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                defaultScope.launch {
                    billingClient.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken).build()
                    ).let { result ->
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            // purchase acknowledged
                            purchase.skus.forEach { setSkuState(it, PurchaseState.Acknowledged) }
                        }
                    }
                }
                _newPurchases.tryEmit(purchase.skus)
            }
            setSkuStateFromPurchase(purchase)
        }
        toUpdate?.filterNot { updatedSkus.contains(it) }
            ?.forEach { setSkuState(it, PurchaseState.Unowned) }
    }

    fun launchBillingFlow(activity: Activity, sku: String) {
        defaultScope.launch {
            billingClient.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder().setSkuDetails(skuDetailsMap[sku]?.value!!).build()
            ).let {
                if (it.responseCode == BillingClient.BillingResponseCode.OK) {
                    billingFlowInProcess.value = true
                }
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun resume() {
        if (!billingFlowInProcess.value && billingClient.isReady) defaultScope.launch { refreshPurchases() }
    }

    companion object {
        @Volatile
        private var sInstance: BillingDataSource? = null
        private val handler = Handler(Looper.getMainLooper())

        @JvmStatic
        fun getInstance(application: Application, defaultScope: CoroutineScope) =
            sInstance ?: synchronized(this) {
                sInstance ?: BillingDataSource(application, defaultScope).also { sInstance = it }
            }
    }

    init {
        billingClient =
            BillingClient.newBuilder(application).setListener(object : PurchasesUpdatedListener {
                override fun onPurchasesUpdated(
                    result: BillingResult, purchases: MutableList<Purchase>?
                ) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                        processPurchaseList(purchases, null)
                        return
                    }
                    billingFlowInProcess.value = false
                }
            }).enablePendingPurchases().build()
        billingClient.startConnection(object : BillingClientStateListener {
            private fun retryBillingServiceConnectionWithExponentialBackoff() {
                handler.postDelayed({ billingClient.startConnection(this) }, reconnectTimer)
                reconnectTimer = min(reconnectTimer * 2, maxReconnectDelay)
            }

            override fun onBillingServiceDisconnected() {
                retryBillingServiceConnectionWithExponentialBackoff()
            }

            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    reconnectTimer = reconnectDelay
                    defaultScope.launch {
                        querySkuDetailsAsync()
                        refreshPurchases()
                    }
                } else retryBillingServiceConnectionWithExponentialBackoff()
            }
        })
    }
}
