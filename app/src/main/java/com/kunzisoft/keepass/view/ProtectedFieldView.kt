package com.kunzisoft.keepass.view

import android.view.View.OnClickListener

interface ProtectedFieldView {
    fun setProtection(protection: Boolean, onUnprotectClickListener: OnClickListener?)
    fun protect()
    fun unprotect()
    fun isCurrentlyProtected(): Boolean
}