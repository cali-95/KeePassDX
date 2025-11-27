package com.kunzisoft.keepass.viewmodels

import androidx.lifecycle.ViewModel
import com.kunzisoft.keepass.database.ContextualDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for the User Verification
 */
class UserVerificationViewModel: ViewModel() {

    private val mUiState = MutableStateFlow<UIState>(UIState.Loading)
    val uiState: StateFlow<UIState> = mUiState

    fun onUserVerificationSucceeded(database: ContextualDatabase?) {
        mUiState.value = UIState.OnUserVerificationSucceeded(database)
    }

    fun onUserVerificationFailed(database: ContextualDatabase? = null) {
        mUiState.value = UIState.OnUserVerificationCanceled(database)
    }

    sealed class UIState {
        object Loading: UIState()
        data class OnUserVerificationSucceeded(
            val database: ContextualDatabase?
        ): UIState()
        data class OnUserVerificationCanceled(
            val database: ContextualDatabase?
        ): UIState()
    }

}