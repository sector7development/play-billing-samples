package com.sector7.chain_reaction.billing

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.min

private const val RECONNECT_TIMER_START_MILLISECONDS = 1L * 1000L
private const val RECONNECT_TIMER_MAX_TIME_MILLISECONDS = 1000L * 60L * 15L // 15 minutes
private const val SKU_DETAILS_REQUERY_TIME = 1000L * 60L * 60L * 4L // 4 hours

class BillingDataSource private constructor(
    application: Application,
    private val defaultScope: CoroutineScope,
    knownInappSKUs: Array<String>?
) : LifecycleObserver, PurchasesUpdatedListener, BillingClientStateListener {
    private val billingClient: BillingClient

    // known SKUs (used to query sku data and validate responses)
    private val knownInappSKUs: List<String> =
        if (knownInappSKUs == null) ArrayList() else listOf(*knownInappSKUs)

    // how long before the data source tries to reconnect to Google play
    private var reconnectMilliseconds = RECONNECT_TIMER_START_MILLISECONDS

    // when was the last successful SkuDetailsResponse?
    private var skuDetailsResponseTime = -SKU_DETAILS_REQUERY_TIME

    private enum class SkuState {
        SKU_STATE_UNPURCHASED, SKU_STATE_PENDING, SKU_STATE_PURCHASED, SKU_STATE_PURCHASED_AND_ACKNOWLEDGED
    }

    // Flows that are mostly maintained so they can be transformed into observables.
    private val skuStateMap: MutableMap<String, MutableStateFlow<SkuState>> = HashMap()
    private val skuDetailsMap: MutableMap<String, MutableStateFlow<SkuDetails?>> = HashMap()

    // Observables that are used to communicate state.
    private val newPurchaseFlow = MutableSharedFlow<List<String>>(extraBufferCapacity = 1)
    private val billingFlowInProcess = MutableStateFlow(false)

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage
        Log.d(TAG, "onBillingSetupFinished: $responseCode $debugMessage")
        when (responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                reconnectMilliseconds = RECONNECT_TIMER_START_MILLISECONDS
                defaultScope.launch {
                    querySkuDetailsAsync()
                    refreshPurchases()
                }
            }
            else -> retryBillingServiceConnectionWithExponentialBackoff()
        }
    }

    override fun onBillingServiceDisconnected() {
        retryBillingServiceConnectionWithExponentialBackoff()
    }

    private fun retryBillingServiceConnectionWithExponentialBackoff() {
        handler.postDelayed(
            { billingClient.startConnection(this@BillingDataSource) }, reconnectMilliseconds
        )
        reconnectMilliseconds =
            min(reconnectMilliseconds * 2, RECONNECT_TIMER_MAX_TIME_MILLISECONDS)
    }

    private fun addSkuFlows(skuList: List<String>?) {
        for (sku in skuList!!) {
            val skuState = MutableStateFlow(SkuState.SKU_STATE_UNPURCHASED)
            val details = MutableStateFlow<SkuDetails?>(null)
            details.subscriptionCount.map { count -> count > 0 } // map count into active/inactive flag
                .distinctUntilChanged() // only react to true<->false changes
                .onEach { isActive -> // configure an action
                    if (isActive && (SystemClock.elapsedRealtime() - skuDetailsResponseTime > SKU_DETAILS_REQUERY_TIME)) {
                        skuDetailsResponseTime = SystemClock.elapsedRealtime()
                        Log.v(TAG, "Skus not fresh, requerying")
                        querySkuDetailsAsync()
                    }
                }.launchIn(defaultScope) // launch it
            skuStateMap[sku] = skuState
            skuDetailsMap[sku] = details
        }
    }

    private fun initializeFlows() {
        addSkuFlows(knownInappSKUs)
    }

    fun getNewPurchases() = newPurchaseFlow.asSharedFlow()

    fun isPurchased(sku: String) =
        skuStateMap[sku]!!.map { skuState -> skuState == SkuState.SKU_STATE_PURCHASED_AND_ACKNOWLEDGED }

    fun canPurchase(sku: String) =
        skuStateMap[sku]!!.combine(skuDetailsMap[sku]!!) { skuState, skuDetails -> skuState == SkuState.SKU_STATE_UNPURCHASED && skuDetails != null }

    fun getSkuTitle(sku: String) =
        skuDetailsMap[sku]!!.mapNotNull { skuDetails -> skuDetails?.title }

    fun getSkuPrice(sku: String) =
        skuDetailsMap[sku]!!.mapNotNull { skuDetails -> skuDetails?.price }

    fun getSkuDescription(sku: String) =
        skuDetailsMap[sku]!!.mapNotNull { skuDetails -> skuDetails?.description }

    private fun onSkuDetailsResponse(
        billingResult: BillingResult, skuDetailsList: List<SkuDetails>?
    ) {
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage
        when (responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                Log.i(TAG, "onSkuDetailsResponse: $responseCode $debugMessage")
                if (skuDetailsList == null || skuDetailsList.isEmpty()) {
                    Log.e(
                        TAG,
                        "onSkuDetailsResponse: Found null or empty SkuDetails. Check to see if the SKUs you requested are correctly published in the Google Play Console."
                    )
                } else {
                    for (skuDetails in skuDetailsList) {
                        val sku = skuDetails.sku
                        val detailsMutableFlow = skuDetailsMap[sku]
                        detailsMutableFlow?.tryEmit(skuDetails) ?: Log.e(TAG, "Unknown sku: $sku")
                    }
                }
            }
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED, BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE, BillingClient.BillingResponseCode.BILLING_UNAVAILABLE, BillingClient.BillingResponseCode.ITEM_UNAVAILABLE, BillingClient.BillingResponseCode.DEVELOPER_ERROR, BillingClient.BillingResponseCode.ERROR -> Log.e(
                TAG, "onSkuDetailsResponse: $responseCode $debugMessage"
            )
            BillingClient.BillingResponseCode.USER_CANCELED -> Log.i(
                TAG, "onSkuDetailsResponse: $responseCode $debugMessage"
            )
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED, BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED, BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> Log.wtf(
                TAG, "onSkuDetailsResponse: $responseCode $debugMessage"
            )
            else -> Log.wtf(TAG, "onSkuDetailsResponse: $responseCode $debugMessage")
        }
        skuDetailsResponseTime =
            if (responseCode == BillingClient.BillingResponseCode.OK) SystemClock.elapsedRealtime() else -SKU_DETAILS_REQUERY_TIME
    }

    private suspend fun querySkuDetailsAsync() {
        if (!knownInappSKUs.isNullOrEmpty()) {
            val skuDetailsResult = billingClient.querySkuDetails(
                SkuDetailsParams.newBuilder().setType(BillingClient.SkuType.INAPP)
                    .setSkusList(knownInappSKUs).build()
            )
            println("Josh: ${skuDetailsResult.billingResult.debugMessage}")
            onSkuDetailsResponse(skuDetailsResult.billingResult, skuDetailsResult.skuDetailsList)
        }
    }

    suspend fun refreshPurchases() {
        Log.d(TAG, "Refreshing purchases.")
        val purchasesResult = billingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP)
        val billingResult = purchasesResult.billingResult
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "Problem getting purchases: " + billingResult.debugMessage)
        } else {
            processPurchaseList(purchasesResult.purchasesList, knownInappSKUs)
        }
        Log.d(TAG, "Refreshing purchases finished.")
    }

    private fun setSkuStateFromPurchase(purchase: Purchase) {
        for (purchaseSku in purchase.skus) {
            val skuStateFlow = skuStateMap[purchaseSku]
            if (null == skuStateFlow) {
                Log.e(
                    TAG,
                    "Unknown SKU $purchaseSku. Check to make sure SKU matches SKUS in the Play developer console."
                )
            } else {
                when (purchase.purchaseState) {
                    Purchase.PurchaseState.PENDING -> skuStateFlow.tryEmit(SkuState.SKU_STATE_PENDING)
                    Purchase.PurchaseState.UNSPECIFIED_STATE -> skuStateFlow.tryEmit(SkuState.SKU_STATE_UNPURCHASED)
                    Purchase.PurchaseState.PURCHASED -> skuStateFlow.tryEmit(if (purchase.isAcknowledged) SkuState.SKU_STATE_PURCHASED_AND_ACKNOWLEDGED else SkuState.SKU_STATE_PURCHASED)
                    else -> Log.e(TAG, "Purchase in unknown state: ${purchase.purchaseState}")
                }
            }
        }
    }

    private fun setSkuState(sku: String, newSkuState: SkuState) {
        skuStateMap[sku]?.tryEmit(newSkuState) ?: Log.e(
            TAG,
            "Unknown SKU $sku. Check to make sure SKU matches SKUS in the Play developer console."
        )
    }

    private fun processPurchaseList(purchases: List<Purchase>?, skusToUpdate: List<String>?) {
        val updatedSkus = HashSet<String>()
        if (null != purchases) {
            for (purchase in purchases) {
                for (sku in purchase.skus) {
                    val skuStateFlow = skuStateMap[sku]
                    if (null == skuStateFlow) {
                        Log.e(
                            TAG,
                            "Unknown SKU $sku. Check to make sure SKU matches SKUs in the Play developer console."
                        )
                        continue
                    }
                    updatedSkus.add(sku)
                }
                val purchaseState = purchase.purchaseState
                if (purchaseState == Purchase.PurchaseState.PURCHASED) {
                    setSkuStateFromPurchase(purchase)
                    defaultScope.launch {
                        if (!purchase.isAcknowledged) {
                            // acknowledge everything --- new purchases are ones not yet acknowledged
                            val billingResult = billingClient.acknowledgePurchase(
                                AcknowledgePurchaseParams.newBuilder()
                                    .setPurchaseToken(purchase.purchaseToken).build()
                            )
                            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                                Log.e(
                                    TAG, "Error acknowledging purchase: ${purchase.skus}"
                                )
                            } else {
                                // purchase acknowledged
                                for (sku in purchase.skus) {
                                    setSkuState(sku, SkuState.SKU_STATE_PURCHASED_AND_ACKNOWLEDGED)
                                }
                            }
                            newPurchaseFlow.tryEmit(purchase.skus)
                        }
                    }
                } else {
                    // make sure the state is set
                    setSkuStateFromPurchase(purchase)
                }
            }
        } else {
            Log.d(TAG, "Empty purchase list.")
        }
        if (null != skusToUpdate) {
            for (sku in skusToUpdate) {
                if (!updatedSkus.contains(sku)) {
                    setSkuState(sku, SkuState.SKU_STATE_UNPURCHASED)
                }
            }
        }
    }

    fun launchBillingFlow(activity: Activity?, sku: String) {
        val skuDetails = skuDetailsMap[sku]?.value
        if (null != skuDetails) {
            val billingFlowParamsBuilder = BillingFlowParams.newBuilder()
            billingFlowParamsBuilder.setSkuDetails(skuDetails)
            defaultScope.launch {
                val br = billingClient.launchBillingFlow(
                    activity!!, billingFlowParamsBuilder.build()
                )
                if (br.responseCode == BillingClient.BillingResponseCode.OK) {
                    billingFlowInProcess.emit(true)
                } else {
                    Log.e(TAG, "Billing failed: + " + br.debugMessage)
                }
            }
        } else {
            Log.e(TAG, "SkuDetails not found for: $sku")
        }
    }

    fun getBillingFlowInProcess() = billingFlowInProcess.asStateFlow()

    override fun onPurchasesUpdated(billingResult: BillingResult, list: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> if (null != list) {
                processPurchaseList(list, null)
                return
            } else Log.d(TAG, "Null Purchase List Returned from OK response!")
            BillingClient.BillingResponseCode.USER_CANCELED -> Log.i(
                TAG, "onPurchasesUpdated: User canceled the purchase"
            )
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> Log.i(
                TAG, "onPurchasesUpdated: The user already owns this item"
            )
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> Log.e(
                TAG,
                "onPurchasesUpdated: Developer error means that Google Play does not recognize the configuration. If you are just getting started, make sure you have configured the application correctly in the Google Play Console. The SKU product ID must match and the APK you are using must be signed with release keys."
            )
            else -> Log.d(
                TAG, "BillingResult [${billingResult.responseCode}]: ${billingResult.debugMessage}"
            )
        }
        defaultScope.launch { billingFlowInProcess.emit(false) }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun resume() {
        Log.d(TAG, "ON_RESUME")
        if (!billingFlowInProcess.value) {
            if (billingClient.isReady) {
                defaultScope.launch {
                    refreshPurchases()
                }
            }
        }
    }

    companion object {
        private val TAG = "TrivialDrive:" + BillingDataSource::class.java.simpleName

        @Volatile
        private var sInstance: BillingDataSource? = null
        private val handler = Handler(Looper.getMainLooper())

        @JvmStatic
        fun getInstance(
            application: Application, defaultScope: CoroutineScope, knownInappSKUs: Array<String>?
        ) = sInstance ?: synchronized(this) {
            sInstance ?: BillingDataSource(
                application, defaultScope, knownInappSKUs
            ).also { sInstance = it }
        }
    }

    init {
        initializeFlows()
        billingClient =
            BillingClient.newBuilder(application).setListener(this).enablePendingPurchases().build()
        billingClient.startConnection(this)
    }
}
