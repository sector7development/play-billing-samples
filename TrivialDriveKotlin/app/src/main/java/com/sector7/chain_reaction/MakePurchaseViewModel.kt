package com.sector7.chain_reaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MakePurchaseViewModel : ViewModel()

class MakePurchaseViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MakePurchaseViewModel::class.java)) {
            return MakePurchaseViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
