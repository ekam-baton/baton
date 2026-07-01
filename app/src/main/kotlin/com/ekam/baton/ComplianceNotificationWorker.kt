package com.ekam.baton

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * ComplianceNotificationWorker
 *
 * Dispatches a periodic local notification reminding users of their Privacy Policy
 * and Terms of Service obligations. This is mandatory under:
 * - Information Technology (Intermediary Guidelines and Digital Media Ethics Code) Rules, 2021
 *   (as amended in 2026), which require platforms to notify users of their privacy policy,
 *   user agreement, and consequences of non-compliance at least once every 90 days.
 */
class ComplianceNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "baton_compliance_alerts"
        const val NOTIFICATION_ID = 9001
        const val WORK_NAME = "BatonComplianceReminder"
    }

    override suspend fun doWork(): Result {
        return try {
            sendComplianceNotification()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun sendComplianceNotification() {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Deep-link intent into the app when user taps the notification
        val openAppIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("BATON – Policy Reminder")
            .setContentText("Please review our Privacy Policy and Terms of Service to stay informed about your rights and obligations.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "As required by Indian IT Rules, we are reminding you of BATON's Privacy Policy and Terms of Service. " +
                                "You can view these documents in Settings → About. " +
                                "For grievances, contact: grievance@baton-app.in"
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
