package com.kunzisoft.keepass.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PasswordInfo(
    val username: String,
    val password: String,
    val appOrigin: AppOrigin?
): Parcelable