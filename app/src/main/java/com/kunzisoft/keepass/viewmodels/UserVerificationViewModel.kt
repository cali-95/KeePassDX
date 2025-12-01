package com.kunzisoft.keepass.viewmodels

import androidx.lifecycle.ViewModel
import com.kunzisoft.keepass.credentialprovider.UserVerificationData
import com.kunzisoft.keepass.database.element.MasterCredential.CREATOR.getCheckKey
import com.kunzisoft.keepass.database.exception.InvalidCredentialsDatabaseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for the User Verification
 */
class UserVerificationViewModel: ViewModel() {

    private val mUiState = MutableStateFlow<UIState>(UIState.Loading)
    val uiState: StateFlow<UIState> = mUiState

    var dataToVerify: UserVerificationData = UserVerificationData()

    fun checkMainCredential(checkString: String) {
        // Check the password part
        if (dataToVerify.database?.checkKey(getCheckKey(checkString)) == true)
            onUserVerificationSucceeded(dataToVerify)
        else {
            onUserVerificationFailed(dataToVerify, InvalidCredentialsDatabaseException())
        }
        dataToVerify = UserVerificationData()
    }

    fun onUserVerificationSucceeded(dataToVerify: UserVerificationData) {
        mUiState.value = UIState.OnUserVerificationSucceeded(dataToVerify)
    }

    fun onUserVerificationFailed(
        dataToVerify: UserVerificationData = UserVerificationData(),
        error: Throwable? = null
    ) {
        this.dataToVerify = dataToVerify
        mUiState.value = UIState.OnUserVerificationCanceled(dataToVerify, error)
    }

    fun onUserVerificationReceived() {
        mUiState.value = UIState.Loading
    }

    sealed class UIState {
        object Loading: UIState()
        data class OnUserVerificationSucceeded(val dataToVerify: UserVerificationData): UIState()
        data class OnUserVerificationCanceled(
            val dataToVerify: UserVerificationData,
            val error: Throwable?
        ): UIState()
    }

}