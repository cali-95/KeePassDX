package com.kunzisoft.keepass.credentialprovider

import com.kunzisoft.keepass.database.ContextualDatabase
import com.kunzisoft.keepass.database.element.node.NodeId
import com.kunzisoft.keepass.view.ProtectedFieldView

data class UserVerificationData(
    val database: ContextualDatabase? = null,
    val entryId: NodeId<*>? = null,
    val protectedFieldView: ProtectedFieldView? = null,
    val preferenceKey: String? = null
)