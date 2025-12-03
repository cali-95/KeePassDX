package com.kunzisoft.keepass.credentialprovider

import com.kunzisoft.keepass.database.ContextualDatabase
import com.kunzisoft.keepass.database.element.Field
import com.kunzisoft.keepass.database.element.node.NodeId
import com.kunzisoft.keepass.view.ProtectedFieldView

data class UserVerificationData(
    val actionType: UserVerificationActionType,
    val database: ContextualDatabase? = null,
    val entryId: NodeId<*>? = null,
    val field: Field? = null,
    val protectedFieldView: ProtectedFieldView? = null,
    val preferenceKey: String? = null
)

enum class UserVerificationActionType {
    LAUNCH_PASSKEY_CEREMONY,
    SHOW_PROTECTED_FIELD,
    COPY_PROTECTED_FIELD,
    EDIT_ENTRY,
    EDIT_DATABASE_SETTING,
    MERGE_FROM_DATABASE,
    SAVE_TO_DATABASE
}