package com.kunzisoft.keepass.credentialprovider

import com.kunzisoft.keepass.database.ContextualDatabase
import com.kunzisoft.keepass.database.element.node.NodeId

data class UserVerificationData(
    val database: ContextualDatabase? = null,
    val entryId: NodeId<*>? = null
    )