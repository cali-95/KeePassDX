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
package com.kunzisoft.keepass.credentialprovider.passkey

import android.graphics.BlendMode
import android.graphics.drawable.Icon
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import androidx.credentials.provider.PublicKeyCredentialEntry
import com.kunzisoft.encrypt.Base64Helper.Companion.b64Encode
import com.kunzisoft.keepass.R
import com.kunzisoft.keepass.credentialprovider.EntrySelectionHelper.buildIcon
import com.kunzisoft.keepass.credentialprovider.SpecialMode
import com.kunzisoft.keepass.credentialprovider.activity.PasskeyLauncherActivity
import com.kunzisoft.keepass.credentialprovider.passkey.data.PublicKeyCredentialCreationOptions
import com.kunzisoft.keepass.credentialprovider.passkey.data.PublicKeyCredentialRequestOptions
import com.kunzisoft.keepass.credentialprovider.passkey.data.UserVerificationRequirement
import com.kunzisoft.keepass.database.ContextualDatabase
import com.kunzisoft.keepass.database.DatabaseTaskProvider
import com.kunzisoft.keepass.database.exception.RegisterInReadOnlyDatabaseException
import com.kunzisoft.keepass.database.helper.SearchHelper
import com.kunzisoft.keepass.model.SearchInfo
import com.kunzisoft.keepass.settings.PreferencesUtil.isPasskeyAutoSelectEnable
import com.kunzisoft.keepass.view.toastError
import java.io.IOException
import java.time.Instant

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasskeyProviderService : CredentialProviderService() {

    private var mDatabaseTaskProvider: DatabaseTaskProvider? = null
    private var mDatabase: ContextualDatabase? = null
    private lateinit var defaultIcon: Icon
    private var isAutoSelectAllowed: Boolean = false

    override fun onCreate() {
        super.onCreate()

        mDatabaseTaskProvider = DatabaseTaskProvider(this)
        mDatabaseTaskProvider?.registerProgressTask()
        mDatabaseTaskProvider?.onDatabaseRetrieved = { database ->
            this.mDatabase = database
        }

        defaultIcon = Icon.createWithResource(
            this@PasskeyProviderService,
            R.mipmap.ic_launcher_round
        ).apply {
            setTintBlendMode(BlendMode.DST)
        }

        isAutoSelectAllowed = isPasskeyAutoSelectEnable(this)
    }

    override fun onDestroy() {
        mDatabaseTaskProvider?.unregisterProgressTask()
        super.onDestroy()
    }

    private fun buildPasskeySearchInfo(
        relyingParty: String,
        credentialIds: List<String> = listOf()
    ): SearchInfo {
        return SearchInfo().apply {
            this.relyingParty = relyingParty
            this.credentialIds = credentialIds
        }
    }

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>
    ) {
        Log.d(javaClass.simpleName, "onBeginGetCredentialRequest called")
        try {
            processGetCredentialsRequest(request) { response ->
                callback.onResult(response)
            }
        } catch (e: Exception) {
            Log.e(javaClass.simpleName, "onBeginGetCredentialRequest error", e)
            toastError(e)
            callback.onError(GetCredentialUnknownException())
        }
    }

    private fun processGetCredentialsRequest(
        request: BeginGetCredentialRequest,
        callback: (BeginGetCredentialResponse?) -> Unit
    ) {
        var knownOption = false
        for (option in request.beginGetCredentialOptions) {
            when (option) {
                is BeginGetPublicKeyCredentialOption -> {
                    knownOption = true
                    populatePasskeyData(option) { listCredentials ->
                        callback(BeginGetCredentialResponse(listCredentials))
                    }
                }
            }
        }
        if (knownOption.not()) {
            throw IOException("unknown type of beginGetCredentialOption")
        }
    }

    private fun populatePasskeyData(
        option: BeginGetPublicKeyCredentialOption,
        callback: (List<CredentialEntry>) -> Unit
    ) {
        val passkeyEntries: MutableList<CredentialEntry> = mutableListOf()

        val publicKeyCredentialRequestOptions = PublicKeyCredentialRequestOptions(option.requestJson)
        val relyingPartyId = publicKeyCredentialRequestOptions.rpId
        val credentialIdList = publicKeyCredentialRequestOptions.allowCredentials
            .map { b64Encode(it.id) }
        val searchInfo = buildPasskeySearchInfo(relyingPartyId, credentialIdList)
        // TODO remove
        val userVerification = UserVerificationRequirement.REQUIRED//publicKeyCredentialRequestOptions.userVerification
        Log.d(TAG, "Build passkey search for UV $userVerification, " +
                "RP $relyingPartyId and Credential IDs $credentialIdList")
        SearchHelper.checkAutoSearchInfo(
            context = this,
            database = mDatabase,
            searchInfo = searchInfo,
            onItemsFound = { database, items ->
                Log.d(TAG, "Add pending intent for passkey selection with found items")
                for (passkeyEntry in items) {
                    PasskeyLauncherActivity.getPendingIntent(
                        context = applicationContext,
                        specialMode = SpecialMode.SELECTION,
                        nodeId = passkeyEntry.id,
                        appOrigin = passkeyEntry.appOrigin,
                        userVerification = userVerification,
                        userVerifiedWithAuth = false
                    )?.let { usagePendingIntent ->
                        val passkey = passkeyEntry.passkey
                        passkeyEntries.add(
                            PublicKeyCredentialEntry(
                                context = applicationContext,
                                username = passkey?.username ?: "Unknown",
                                icon = passkeyEntry.buildIcon(
                                    this@PasskeyProviderService,
                                    database
                                )?.apply {
                                    setTintBlendMode(BlendMode.DST)
                                } ?: defaultIcon,
                                pendingIntent = usagePendingIntent,
                                beginGetPublicKeyCredentialOption = option,
                                displayName = passkeyEntry.getVisualTitle(),
                                isAutoSelectAllowed = isAutoSelectAllowed
                            )
                        )
                    }
                }
                callback(passkeyEntries)
            },
            onItemNotFound = { _ ->
                Log.w(TAG, "No passkey found in the database with this relying party : $relyingPartyId")
                if (credentialIdList.isEmpty()) {
                    Log.d(TAG, "Add pending intent for passkey selection in opened database")
                    PasskeyLauncherActivity.getPendingIntent(
                        context = applicationContext,
                        specialMode = SpecialMode.SELECTION,
                        searchInfo = searchInfo,
                        userVerification = userVerification,
                        userVerifiedWithAuth = false
                    )?.let { pendingIntent ->
                        passkeyEntries.add(
                            PublicKeyCredentialEntry(
                                context = applicationContext,
                                username = getString(R.string.passkey_database_username),
                                displayName = getString(R.string.passkey_selection_description),
                                icon = defaultIcon,
                                pendingIntent = pendingIntent,
                                beginGetPublicKeyCredentialOption = option,
                                lastUsedTime = Instant.now(),
                                isAutoSelectAllowed = isAutoSelectAllowed
                            )
                        )
                    }
                    callback(passkeyEntries)
                } else {
                    throw IOException(
                        getString(
                            R.string.error_passkey_credential_id,
                            relyingPartyId,
                            credentialIdList
                        )
                    )
                }
            },
            onDatabaseClosed = {
                Log.d(TAG, "Add pending intent for passkey selection in closed database")
                // Database is locked, a public key credential entry is shown to unlock it
                PasskeyLauncherActivity.getPendingIntent(
                    context = applicationContext,
                    specialMode = SpecialMode.SELECTION,
                    searchInfo = searchInfo,
                    userVerifiedWithAuth = true
                )?.let { pendingIntent ->
                    passkeyEntries.add(
                        PublicKeyCredentialEntry(
                            context = applicationContext,
                            username = getString(R.string.passkey_database_username),
                            displayName = getString(R.string.passkey_locked_database_description),
                            icon = defaultIcon,
                            pendingIntent = pendingIntent,
                            beginGetPublicKeyCredentialOption = option,
                            lastUsedTime = Instant.now(),
                            isAutoSelectAllowed = isAutoSelectAllowed
                        )
                    )
                }
                callback(passkeyEntries)
            }
        )
    }

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        Log.d(javaClass.simpleName, "onBeginCreateCredentialRequest called")
        try {
            processCreateCredentialRequest(request) {
                callback.onResult(BeginCreateCredentialResponse(it))
            }
        } catch (e: Exception) {
            Log.e(javaClass.simpleName, "onBeginCreateCredentialRequest error", e)
            toastError(e)
            callback.onError(CreateCredentialUnknownException(e.localizedMessage))
        }
    }

    private fun processCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        callback: (List<CreateEntry>) -> Unit
    ) {
        when (request) {
            is BeginCreatePublicKeyCredentialRequest -> {
                // Request is passkey type
                handleCreatePasskeyQuery(request, callback)
            }
            else -> {
                // request type not supported
                throw IOException("unknown type of BeginCreateCredentialRequest")
            }
        }
    }

    private fun MutableList<CreateEntry>.addPendingIntentCreationNewEntry(
        accountName: String,
        searchInfo: SearchInfo?,
        userVerification: UserVerificationRequirement
    ) {
        Log.d(TAG, "Add pending intent for registration in opened database to create new item")
        // TODO add a setting to directly store in a specific group
        PasskeyLauncherActivity.getPendingIntent(
            context = applicationContext,
            specialMode = SpecialMode.REGISTRATION,
            searchInfo = searchInfo,
            userVerification = userVerification,
            userVerifiedWithAuth = false
        )?.let { pendingIntent ->
            this.add(
                CreateEntry(
                    accountName = accountName,
                    icon = defaultIcon,
                    pendingIntent = pendingIntent,
                    description = getString(R.string.passkey_creation_description)
                )
            )
        }
    }

    private fun handleCreatePasskeyQuery(
        request: BeginCreatePublicKeyCredentialRequest,
        callback: (List<CreateEntry>) -> Unit
    ) {
        val databaseName = mDatabase?.name
        val accountName =
            if (databaseName?.isBlank() != false)
                getString(R.string.passkey_database_username)
            else databaseName
        val createEntries: MutableList<CreateEntry> = mutableListOf()
        val publicKeyCredentialCreationOptions = PublicKeyCredentialCreationOptions(
            requestJson = request.requestJson,
            clientDataHash = request.clientDataHash
        )
        val relyingPartyId = publicKeyCredentialCreationOptions.relyingPartyEntity.id
        val searchInfo = buildPasskeySearchInfo(relyingPartyId)
        val userVerification = publicKeyCredentialCreationOptions.authenticatorSelection.userVerification
        Log.d(TAG, "Build passkey search for relying party $relyingPartyId")
        SearchHelper.checkAutoSearchInfo(
            context = this,
            database = mDatabase,
            searchInfo = searchInfo,
            onItemsFound = { database, items ->
                if (database.isReadOnly) {
                    throw RegisterInReadOnlyDatabaseException()
                } else {
                    // To create a new entry
                    createEntries.addPendingIntentCreationNewEntry(
                        accountName = accountName,
                        searchInfo = searchInfo,
                        userVerification = userVerification
                    )
                    /* TODO Overwrite
                    // To select an existing entry and permit an overwrite
                    Log.w(TAG, "Passkey already registered")
                    for (entryInfo in items) {
                        PasskeyHelper.getPendingIntent(
                            context = applicationContext,
                            specialMode = SpecialMode.REGISTRATION,
                            searchInfo = searchInfo,
                            passkeyEntryNodeId = entryInfo.id
                        )?.let { createPendingIntent ->
                            createEntries.add(
                                CreateEntry(
                                    accountName = accountName,
                                    pendingIntent = createPendingIntent,
                                    description = getString(
                                        R.string.passkey_update_description,
                                        entryInfo.passkey?.displayName
                                    )
                                )
                            )
                        }
                    }*/
                }
                callback(createEntries)
            },
            onItemNotFound = { database ->
                // To create a new entry
                if (database.isReadOnly) {
                    throw RegisterInReadOnlyDatabaseException()
                } else {
                    createEntries.addPendingIntentCreationNewEntry(
                        accountName = accountName,
                        searchInfo = searchInfo,
                        userVerification = userVerification
                    )
                }
                callback(createEntries)
            },
            onDatabaseClosed = {
                // Launch the passkey launcher activity to open the database
                Log.d(TAG, "Add pending intent for passkey registration in closed database")
                PasskeyLauncherActivity.getPendingIntent(
                    context = applicationContext,
                    specialMode = SpecialMode.REGISTRATION,
                    userVerifiedWithAuth = true
                )?.let { pendingIntent ->
                    createEntries.add(
                        CreateEntry(
                            accountName = accountName,
                            icon = defaultIcon,
                            pendingIntent = pendingIntent,
                            description = getString(R.string.passkey_locked_database_description)
                        )
                    )
                }
                callback(createEntries)
            }
        )
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>
    ) {
        // nothing to do
    }

    companion object {
        private val TAG = PasskeyProviderService::class.java.simpleName
    }
}