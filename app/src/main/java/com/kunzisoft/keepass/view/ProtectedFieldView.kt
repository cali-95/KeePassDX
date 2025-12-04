package com.kunzisoft.keepass.view

import android.view.View.OnClickListener

interface ProtectedFieldView {
    fun setProtection(
        protection: Boolean,
        isCurrentlyProtected: Boolean,
        onUnprotectClickListener: OnClickListener?
    )
    fun isCurrentlyProtected(): Boolean
    fun protect()
    fun unprotect()
}