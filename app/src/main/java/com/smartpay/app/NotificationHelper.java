package com.smartpay.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * NotificationHelper - Posts OS-level notifications for SmartPay events.
 *
 * Channel: CHANNEL_PAYMENTS ("Money Received" notifications)
 *
 * Icon strategy:
 *   - smallIcon  → ic_launcher_foreground  (the vector/shape layer, white on transparent,
 *                   required by the status bar)
 *   - largeIcon  → @mipmap/ic_launcher       (the full adaptive-icon bitmap, shown in the
 *                   expanded notification drawer — background + foreground combined)
 *
 * Tapping the notification opens NotificationsActivity, back-stacked behind HomeActivity
 * so the system Back button works naturally.
 *
 * Android 13+ (API 33+): POST_NOTIFICATIONS is a runtime permission. This helper checks
 * for it before posting; the HomeActivity requests it on first launch.
 */
public final class NotificationHelper {

    public static final String CHANNEL_ID = "smartpay_payments";
    private static final String CHANNEL_NAME = "Money Received";
    private static final String CHANNEL_DESC = "Alerts when you receive money on SmartPay";

    /** Auto-incrementing ID so multiple rapid notifications don't overwrite each other. */
    private static final AtomicInteger notifIdCounter = new AtomicInteger(1000);

    private NotificationHelper() {}

    /**
     * Ensures the notification channel exists (safe to call repeatedly; no-op after first call).
     * Must be called early — e.g. from Application.onCreate or before posting.
     */
    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH   // Heads-up on lock screen
            );
            channel.setDescription(CHANNEL_DESC);
            channel.enableVibration(true);
            channel.enableLights(true);

            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Posts an OS notification for a received payment.
     *
     * @param context     Any context (Application context preferred from background code).
     * @param title       Notification title, e.g. "Money Received".
     * @param message     Body text, e.g. "Received ₹ 1,500.00 from Alice".
     */
    public static void postPaymentReceived(Context context, String title, String message) {
        // Ensure channel exists before posting
        createChannel(context);

        // Check runtime permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                // Permission not granted yet — silently skip OS notification;
                // the in-app bell will still update via Firebase.
                return;
            }
        }

        // Tap action: open NotificationsActivity, back-stacked behind HomeActivity
        Intent notifIntent = new Intent(context, NotificationsActivity.class);
        notifIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notifIdCounter.get(),   // unique request code per notification
                notifIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                // smallIcon: must be a transparent-background vector/drawable for the status bar
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                // largeIcon: the full app icon bitmap shown in the notification drawer
                .setLargeIcon(
                        android.graphics.BitmapFactory.decodeResource(
                                context.getResources(), R.mipmap.ic_launcher))
                .setContentTitle(title != null ? title : "Money Received")
                .setContentText(message != null ? message : "You received a payment on SmartPay")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)        // dismiss when tapped
                .setContentIntent(pendingIntent);

        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        nm.notify(notifIdCounter.getAndIncrement(), builder.build());
    }
}
