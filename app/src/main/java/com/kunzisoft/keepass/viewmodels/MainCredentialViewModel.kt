package com.kunzisoft.keepass.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.kunzisoft.keepass.database.MainCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for the Main Credential Dialog
 * Easily retrieves main credential from the database identified by its URI
 */
class MainCredentialViewModel: ViewModel() {

    private val mUiState = MutableStateFlow<UIState>(UIState.Loading)
    val uiState: StateFlow<UIState> = mUiState

    fun validateMainCredential(
        databaseUri: Uri,
        mainCredential: MainCredential
    ) {
        mUiState.value = UIState.OnMainCredentialValidated(databaseUri, mainCredential)
    }

    fun cancelMainCredential(
        databaseUri: Uri
    ) {
        mUiState.value = UIState.OnMainCredentialCanceled(databaseUri, MainCredential())
    }

    sealed class UIState {
        object Loading: UIState()
        data class OnMainCredentialValidated(
            val databaseUri: Uri,
            val mainCredential: MainCredential
        ): UIState()
        data class OnMainCredentialCanceled(
            val databaseUri: Uri,
            val mainCredential: MainCredential
        ): UIState()
    }

}