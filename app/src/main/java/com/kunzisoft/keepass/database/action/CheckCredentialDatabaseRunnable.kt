/*
 * Copyright 2025 Jeremy Jamet / Kunzisoft.
 *     
 * This file is part of KeePassDX.
 *
 *  KeePassDX is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  KeePassDX is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with KeePassDX.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.kunzisoft.keepass.database.action

import android.content.Context
import android.net.Uri
import com.kunzisoft.keepass.database.ContextualDatabase
import com.kunzisoft.keepass.database.MainCredential
import com.kunzisoft.keepass.database.exception.DatabaseInputException
import com.kunzisoft.keepass.database.exception.UnknownDatabaseLocationException
import com.kunzisoft.keepass.hardware.HardwareKey
import com.kunzisoft.keepass.tasks.ActionRunnable
import com.kunzisoft.keepass.tasks.ProgressTaskUpdater
import com.kunzisoft.keepass.utils.getUriInputStream

class CheckCredentialDatabaseRunnable(
    private val context: Context,
    private val mDatabase: ContextualDatabase,
    private val mDatabaseUri: Uri,
    private val mMainCredential: MainCredential,
    private val mChallengeResponseRetriever: (hardwareKey: HardwareKey, seed: ByteArray?) -> ByteArray,
    private val progressTaskUpdater: ProgressTaskUpdater?
) : ActionRunnable() {

    var afterCheckCredential : ((Result) -> Unit)? = null

    override fun onStartRun() {}

    override fun onActionRun() {
        try {
            val contentResolver = context.contentResolver
            mDatabase.fileUri = mDatabaseUri
            mDatabase.checkMasterKey(
                databaseStream = contentResolver.getUriInputStream(mDatabaseUri)
                    ?: throw UnknownDatabaseLocationException(),
                masterCredential = mMainCredential.toMasterCredential(contentResolver),
                challengeResponseRetriever = mChallengeResponseRetriever,
                progressTaskUpdater = progressTaskUpdater
            )
        } catch (e: DatabaseInputException) {
            setError(e)
        }
    }

    override fun onFinishRun() {
        afterCheckCredential?.invoke(result)
    }
}
