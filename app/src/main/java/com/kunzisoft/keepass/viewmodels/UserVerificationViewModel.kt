package com.kunzisoft.keepass.viewmodels

import androidx.lifecycle.ViewModel
import com.kunzisoft.keepass.credentialprovider.UserVerificationData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for the User Verification
 */
class UserVerificationViewModel: ViewModel() {

    private val mUiState = MutableStateFlow<UIState>(UIState.Loading)
    val uiState: StateFlow<UIState> = mUiState

    fun onUserVerificationSucceeded(dataToVerify: UserVerificationData) {
        mUiState.value = UIState.OnUserVerificationSucceeded(dataToVerify)
    }

    fun onUserVerificationFailed(dataToVerify: UserVerificationData = UserVerificationData()) {
        mUiState.value = UIState.OnUserVerificationCanceled(dataToVerify)
    }

    fun onUserVerificationReceived() {
        mUiState.value = UIState.Loading
    }

    sealed class UIState {
        object Loading: UIState()
        data class OnUserVerificationSucceeded(val dataToVerify: UserVerificationData): UIState()
        data class OnUserVerificationCanceled(val dataToVerify: UserVerificationData): UIState()
    }

}