package com.sector7.chain_reaction

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.sector7.chain_reaction.TrivialDriveRepository.Companion.SKU_TABLET_BOARDS

class MakePurchaseViewModel(private val tdr: TrivialDriveRepository) : ViewModel() {
    companion object {
        private val skuToResourceIdMap = hashMapOf(SKU_TABLET_BOARDS to R.drawable.upgrade_app)
    }

    class ScootyTails internal constructor(val sku: String, tdr: TrivialDriveRepository) {
        val description = tdr.getSkuDescription(sku).asLiveData()
        val price = tdr.getSkuPrice(sku).asLiveData()
        val iconDrawableId = skuToResourceIdMap[sku]!!
    }

    fun getSkuDetails(sku: String) = ScootyTails(sku, tdr)

    fun canBuySku(sku: String) = tdr.canPurchase(sku).asLiveData()

    fun isPurchased(sku: String) = tdr.isPurchased(sku).asLiveData()

    fun buySku(activity: Activity, sku: String) {
        tdr.buySku(activity, sku)
    }
}

class MakePurchaseViewModelFactory(private val trivialDriveRepository: TrivialDriveRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MakePurchaseViewModel::class.java)) {
            return MakePurchaseViewModel(trivialDriveRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
