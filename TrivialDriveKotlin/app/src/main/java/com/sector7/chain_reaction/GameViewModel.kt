package com.sector7.chain_reaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.sector7.chain_reaction.TrivialDriveRepository.Companion.SKU_TABLET_BOARDS
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn

class GameViewModel(private val tdr: TrivialDriveRepository) : ViewModel() {
    val isPremium get() = tdr.isPurchased(SKU_TABLET_BOARDS).asLiveData()

    class GameViewModelFactory(private val trivialDriveRepository: TrivialDriveRepository) :
        ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
                return GameViewModel(trivialDriveRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
