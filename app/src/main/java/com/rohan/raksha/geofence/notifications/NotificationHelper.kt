package com.rohan.raksha.geofence.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.text.DateFormat
import java.util.Date
import com.rohan.raksha.geofence.AlertActivity
import com.rohan.raksha.geofence.MainActivity
import com.rohan.raksha.geofence.data.SavedLocation
import com.rohan.raksha.geofence.geofence.GeofenceConstants

class NotificationHelper(private val context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val serviceChannel = NotificationChannel(
            GeofenceConstants.NOTIFICATION_CHANNEL_SERVICE,
            "Safe zone monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while Raksha is watching your safe zone"
            setShowBadge(false)
        }

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val alertChannel = NotificationChannel(
            GeofenceConstants.NOTIFICATION_CHANNEL_ALERT,
            "Safe zone alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Full-screen alarm when you leave your safe zone"
            setSound(alarmSound, alarmAttrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 400, 700, 400, 700)
            enableLights(true)
            lightColor = Color.RED
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        // Remove the channel from older builds: its settings can no longer be changed and may be
        // silent / low importance, which makes the alert invisible.
        notificationManager.deleteNotificationChannel(GeofenceConstants.NOTIFICATION_CHANNEL_ALERT_LEGACY)
        notificationManager.createNotificationChannels(listOf(serviceChannel, alertChannel))
    }

    private fun prefs() =
        context.getSharedPreferences(GeofenceConstants.PREFS, Context.MODE_PRIVATE)

    private fun recordAlertInfo(message: String) {
        val time = DateFormat.getTimeInstance().format(Date())
        prefs().edit().putString("alert_info", "$time  $message").apply()
    }

    /** Null when alerts can be shown, otherwise a human-readable reason. */
    fun alertBlockedReason(): String? {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return "Notifications are turned off for Raksha"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(GeofenceConstants.NOTIFICATION_CHANNEL_ALERT)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return "The \"Safe zone alerts\" notification category is turned off"
            }
        }
        return null
    }

    private fun mainActivityPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Ongoing notification required by the foreground tracking service. */
    fun createTrackingNotification(): Notification =
        NotificationCompat.Builder(context, GeofenceConstants.NOTIFICATION_CHANNEL_SERVICE)
            .setContentTitle("Raksha safe zone is active")
            .setContentText("Watching your safe zone")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(mainActivityPendingIntent())
            .build()

    /**
     * Alarm-style notification with a full-screen intent. On a locked / idle phone Android
     * opens [AlertActivity] over the lock screen; on an unlocked phone it shows a heads-up.
     */
    fun showExitAlert(location: SavedLocation): Boolean {
        val blocked = alertBlockedReason()
        if (blocked != null) {
            recordAlertInfo("Alert NOT shown: $blocked")
            return false
        }
        val notificationId = GeofenceConstants.ALERT_NOTIFICATION_BASE_ID + location.id
        val alertIntent = AlertActivity.createIntent(context, location.id, location.name)
        val fullScreen = PendingIntent.getActivity(
            context, notificationId, alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, GeofenceConstants.NOTIFICATION_CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("You left your safe zone")
            .setContentText("You are outside \"${location.name}\".")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFFFF2541.toInt())
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setAutoCancel(true)
            .setTimeoutAfter(GeofenceConstants.ALERT_AUTO_CANCEL_MS)
            .build()
        // keep ringing until the user reacts (or the timeout above hits)
        notification.flags = notification.flags or Notification.FLAG_INSISTENT

        return try {
            notificationManager.notify(notificationId, notification)
            recordAlertInfo("Alert sent for \"${location.name}\"")
            true
        } catch (e: Exception) {
            recordAlertInfo("Alert failed: ${e.javaClass.simpleName}")
            false
        }
    }

    fun cancelAlert(locationId: Int) {
        notificationManager.cancel(GeofenceConstants.ALERT_NOTIFICATION_BASE_ID + locationId)
    }
}
