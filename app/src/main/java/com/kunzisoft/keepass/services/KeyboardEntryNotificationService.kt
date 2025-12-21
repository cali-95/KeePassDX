/*
 * Copyright 2019 Jeremy Jamet / Kunzisoft.
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
package com.kunzisoft.keepass.services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import com.kunzisoft.keepass.R
import com.kunzisoft.keepass.credentialprovider.magikeyboard.MagikeyboardService
import com.kunzisoft.keepass.credentialprovider.magikeyboard.MagikeyboardService.Companion.getSwitchMagikeyboardIntent
import com.kunzisoft.keepass.credentialprovider.magikeyboard.MagikeyboardService.Companion.isAutoSwitchMagikeyboardAllowed
import com.kunzisoft.keepass.credentialprovider.magikeyboard.MagikeyboardService.Companion.isMagikeyboardActivated
import com.kunzisoft.keepass.settings.PreferencesUtil
import com.kunzisoft.keepass.timeout.TimeoutHelper
import com.kunzisoft.keepass.utils.LOCK_ACTION

class KeyboardEntryNotificationService : LockNotificationService() {

    override val notificationId = 486
    private var mNotificationTimeoutMilliSecs: Long = 0

    private var pendingDeleteIntent: PendingIntent? = null

    override fun retrieveChannelId(): String {
        return CHANNEL_MAGIKEYBOARD_ID
    }

    override fun retrieveChannelName(): String {
        return getString(R.string.magic_keyboard_title)
    }

    private fun stopNotificationAndSendLockIfNeeded() {
        // Clear the entry if define in preferences
        if (PreferencesUtil.isClearKeyboardNotificationEnable(this)) {
            sendBroadcast(Intent(LOCK_ACTION))
        }
        // Stop the service
        stopService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (this.isMagikeyboardActivated().not())
            stopService()

        //Get settings
        mNotificationTimeoutMilliSecs = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(getString(R.string.keyboard_entry_timeout_key),
                getString(R.string.timeout_default))?.toLong() ?: TimeoutHelper.DEFAULT_TIMEOUT

        when {
            intent == null -> Log.w(TAG, "null intent")
            ACTION_CLEAN_KEYBOARD_ENTRY == intent.action -> {
                stopNotificationAndSendLockIfNeeded()
            }
            else -> {
                notificationManager?.cancel(notificationId)
                newNotification(intent.getStringExtra(TITLE_INFO_KEY))
            }
        }
        return START_NOT_STICKY
    }

    private fun newNotification(title: String?) {

        val deleteIntent = Intent(this, KeyboardEntryNotificationService::class.java).apply {
            action = ACTION_CLEAN_KEYBOARD_ENTRY
        }
        pendingDeleteIntent = PendingIntent.getService(this, 0, deleteIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val pendingIntent: PendingIntent? =
            if (isAutoSwitchMagikeyboardAllowed(this)) {
                buildActivityPendingIntent(getSwitchMagikeyboardIntent(this))
            } else null

        val entryTitle = title ?: getString(R.string.keyboard_notification_entry_content_title_text)
        val builder = buildNewNotification()
                .setSmallIcon(R.drawable.notification_ic_keyboard_key_24dp)
                .setContentTitle(getString(R.string.keyboard_notification_entry_content_title, entryTitle))
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(pendingDeleteIntent)

        checkNotificationsPermission(this, PreferencesUtil.isKeyboardNotificationEntryEnable(this)) {
            notificationManager?.notify(notificationId, builder.build())
        }

        // Timeout only if notification clear is available
        if (PreferencesUtil.isClearKeyboardNotificationEnable(this)) {
            if (mNotificationTimeoutMilliSecs != TimeoutHelper.NEVER) {
                defineTimerJob(
                    builder,
                    NotificationServiceType.KEYBOARD,
                    mNotificationTimeoutMilliSecs
                ) {
                    stopNotificationAndSendLockIfNeeded()
                }
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        MagikeyboardService.removeEntry(this)

        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Remove the entry from the keyboard
        MagikeyboardService.removeEntry(this)

        pendingDeleteIntent?.cancel()

        super.onDestroy()
    }

    companion object {

        private const val TAG = "KeyboardEntryNotifSrv"

        private const val CHANNEL_MAGIKEYBOARD_ID = "com.kunzisoft.keepass.notification.channel.magikeyboard"
        private const val TITLE_INFO_KEY = "TITLE_INFO_KEY"
        private const val ACTION_CLEAN_KEYBOARD_ENTRY = "ACTION_CLEAN_KEYBOARD_ENTRY"

        fun launchNotificationIfAllowed(context: Context, title: String) {

            var startService = false
            val intent = Intent(context, KeyboardEntryNotificationService::class.java)

            // Show the notification if allowed in Preferences
            if (PreferencesUtil.isKeyboardNotificationEntryEnable(context)
                && context.isMagikeyboardActivated()) {
                startService = true
                context.startService(intent.apply {
                    putExtra(TITLE_INFO_KEY, title)
                })
            }

            if (!startService)
                context.stopService(intent)
        }
    }

}
