package com.sector7.chain_reaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData

class MainActivityViewModel(private val tdr: BillingRepository) : ViewModel() {
    val messages get() = tdr.messages.asLiveData()
    val billingLifecycleObserver get() = tdr.billingLifecycleObserver

    class MainActivityViewModelFactory(private val trivialDriveRepository: BillingRepository) :
        ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainActivityViewModel::class.java)) {
                return MainActivityViewModel(trivialDriveRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
