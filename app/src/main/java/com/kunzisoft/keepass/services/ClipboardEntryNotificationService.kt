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
package com.kunzisoft.keepass.services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.kunzisoft.keepass.R
import com.kunzisoft.keepass.model.OtpModel
import com.kunzisoft.keepass.otp.OtpElement
import com.kunzisoft.keepass.timeout.ClipboardHelper
import com.kunzisoft.keepass.utils.getParcelableExtraCompat
import com.kunzisoft.keepass.utils.getParcelableList
import com.kunzisoft.keepass.utils.putParcelableList

class ClipboardEntryNotificationService : LockNotificationService() {

    override val notificationId = 485
    private var clipboardHelper: ClipboardHelper? = null

    override fun retrieveChannelId(): String {
        return CHANNEL_CLIPBOARD_ID
    }

    override fun retrieveChannelName(): String {
        return getString(R.string.clipboard)
    }

    override fun onCreate() {
        super.onCreate()
        clipboardHelper = ClipboardHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val otpModels: List<OtpModel>? = intent?.getParcelableList(EXTRA_LIST_OTP)
        val otpModelToCopy: OtpModel? = intent?.getParcelableExtraCompat(EXTRA_OTP_TO_COPY)

        when (intent?.action) {
            null -> Log.w(TAG, "null intent")
            ACTION_NEW_NOTIFICATION -> {
                if (otpModels != null && otpModels.isNotEmpty()) {
                    newNotification(otpModels)
                }
            }
            ACTION_COPY_CLIPBOARD -> {
                otpModelToCopy?.let {
                    copyToClipboard(OtpElement(otpModelToCopy).token)
                }
                stopSelf()
            }
            else -> {}
        }
        return START_NOT_STICKY
    }

    private fun newNotification(otpModels: List<OtpModel>) {
        // Retrieve the first OTP
        val firstOtpModel = otpModels[0]
        val builder = buildNewNotification()
                .setSmallIcon(R.drawable.notification_ic_clipboard_key_24dp)
                .setContentTitle(firstOtpModel.toString())
                .setAutoCancel(false)
        builder.setContentText(
            getString(
            R.string.select_to_copy,
            firstOtpModel.toString()
            )
        )
        builder.setContentIntent(buildCopyPendingIntent(firstOtpModel))
        // Add others OTP
        if (otpModels.size > 1) {
            for (i in 1..<otpModels.size) {
                builder.addAction(
                    R.drawable.notification_ic_clipboard_key_24dp,
                    otpModels[i].toString(),
                    buildCopyPendingIntent(otpModels[i])
                )
            }
        }

        notificationManager?.notify(notificationId, builder.build())
    }

    private fun buildCopyPendingIntent(otpToCopy: OtpModel): PendingIntent {
        return PendingIntent.getService(
            this, 0,
            Intent(
                this,
                ClipboardEntryNotificationService::class.java
            ).apply {
                action = ACTION_COPY_CLIPBOARD
                putExtra(EXTRA_OTP_TO_COPY, otpToCopy)
            },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
    }

    private fun copyToClipboard(otpToCopy: String) {
        clipboardHelper?.copyToClipboard(
            getString(R.string.entry_otp),
            otpToCopy
        )
    }

    companion object {

        private val TAG = ClipboardEntryNotificationService::class.simpleName

        private const val CHANNEL_CLIPBOARD_ID = "com.kunzisoft.keepass.notification.channel.clipboard"
        private const val EXTRA_LIST_OTP = "com.kunzisoft.keepass.EXTRA_LIST_OTP"
        private const val EXTRA_OTP_TO_COPY = "com.kunzisoft.keepass.EXTRA_OTP_TO_COPY"

        const val ACTION_NEW_NOTIFICATION = "com.kunzisoft.keepass.ACTION_NEW_NOTIFICATION"
        const val ACTION_COPY_CLIPBOARD = "com.kunzisoft.keepass.ACTION_COPY_CLIPBOARD"

        fun launchNotificationIfAllowed(context: Context, otpList: List<OtpModel>) {
            var startService = false
            val intent = Intent(
                context,
                ClipboardEntryNotificationService::class.java
            )
            if (otpList.isNotEmpty()) {
                checkNotificationsPermission(context, showError = false) {
                    startService = true
                    context.startService(intent.apply {
                        action = ACTION_NEW_NOTIFICATION
                        putParcelableList(EXTRA_LIST_OTP, otpList)
                    })
                }
            }
            if (!startService)
                context.stopService(intent)
        }
    }
}
