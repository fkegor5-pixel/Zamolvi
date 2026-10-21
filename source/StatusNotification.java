package ru.replyai.android;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Log;

/**
 * Ongoing notification in the shade: status, messages sent today and in total,
 * «Включить» / «Остановить» and «Обновить».
 *
 * It is a plain notification, not a foreground service: auto-replies run on the VPS,
 * so the phone does not need to keep anything running. The user can hide it with the
 * system setting of the «Статус автоответчика» channel.
 */
final class StatusNotification {
    private static final String TAG = "ZamolviNotification";
    // v2: default importance (icon in the status bar, not folded into «silent»), still without sound.
    static final String CHANNEL = "replyai_status_v2";
    private static final String OLD_CHANNEL = "replyai_status";
    static final int ID = 2801;
    private static final String TILE_ASKED = "tile_add_asked";
    static final String TILE_STATE = "tile_state";
    private static final String PERMISSION = "android.permission.POST_NOTIFICATIONS";
    private static final String ASKED = "notification_permission_asked";

    private StatusNotification() { }

    /** Notifications are allowed by the system and the channel is not switched off. */
    static boolean allowed(Context c) {
        return why(c) == null;
    }

    /** null when the notification can show; otherwise a short reason it cannot. */
    static String why(Context c) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            return "Нет разрешения на уведомления (Android 13+).";
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || !nm.areNotificationsEnabled()) {
            return "Уведомления приложения выключены в системе.";
        }
        NotificationChannel channel = nm.getNotificationChannel(CHANNEL);
        if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
            return "Канал «Статус автоответчика» выключен в настройках.";
        }
        return null;
    }

    /** Re-ask for the notification permission, even if it was asked before. */
    static void requestPermission(Activity a) {
        if (Build.VERSION.SDK_INT < 33 || a.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED) return;
        a.getSharedPreferences("replyai_features", 0).edit().putBoolean(ASKED, true).apply();
        try {
            a.requestPermissions(new String[] { PERMISSION }, 2801);
        } catch (RuntimeException error) {
            Log.w(TAG, "Cannot request permission", error);
        }
    }

    /** Opens the system notification settings for this app. */
    static void openSettings(Context c) {
        try {
            Intent intent = new Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                    .putExtra("android.provider.extra.APP_PACKAGE", c.getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(intent);
        } catch (RuntimeException error) {
            Log.w(TAG, "Cannot open notification settings", error);
        }
    }

    static void show(Context c, AutoReplyState.Snapshot s, String progress) {
        try {
            if (!allowed(c)) return;
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            ensureChannel(nm);
            int icon = icon(c);
            String text = progress != null ? progress : s.counters();
            String footer = s.footer();

            Notification.Builder b = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(icon)
                    .setContentTitle(I18n.t(c, s.status()))
                    .setContentText(I18n.t(c, text))
                    .setStyle(new Notification.BigTextStyle()
                            .bigText(I18n.t(c, footer.isEmpty() ? text : text + "\n" + footer)))
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setLocalOnly(true)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setColor(s.running() ? 0xFF0E8C87 : 0xFF66707A);
            PendingIntent open = s.awaiting > 0 ? openApprovals(c) : openApp(c);
            if (open != null) b.setContentIntent(open);

            if (progress == null && s.ready()) {
                b.addAction(s.enabled
                        ? action(c, icon, "Остановить", ControlReceiver.ACTION_DISABLE, 22)
                        : action(c, icon, "Включить", ControlReceiver.ACTION_ENABLE, 21));
            }
            if (progress == null && s.configured) {
                b.addAction(action(c, icon, "Обновить", ControlReceiver.ACTION_REFRESH, 23));
            }
            if (progress == null && s.awaiting > 0) {
                b.addAction(new Notification.Action.Builder(Icon.createWithResource(c, icon),
                        I18n.t(c, "Черновики") + " (" + s.awaiting + ")", openApprovals(c)).build());
            }
            nm.notify(ID, b.build());
        } catch (RuntimeException error) {
            Log.w(TAG, "Cannot show notification", error);
        }
    }

    /**
     * Android 13+: asks for the notification permission once, from the main menu; when that is
     * settled, offers once to add the Quick Settings tile (system dialog, Android 13+).
     */
    static void askOnce(Activity a) {
        android.content.SharedPreferences p = a.getSharedPreferences("replyai_features", 0);
        if (Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED
                && !p.getBoolean(ASKED, false)) {
            p.edit().putBoolean(ASKED, true).apply();
            requestPermission(a);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && !p.getBoolean(TILE_ASKED, false)) {
            p.edit().putBoolean(TILE_ASKED, true).apply();
            requestTile(a);
        }
    }

    static boolean permitted(Context c) {
        return Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Is the status notification on screen now? */
    static boolean visible(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return false;
        try {
            for (android.service.notification.StatusBarNotification n : nm.getActiveNotifications()) {
                if (n.getId() == ID) return true;
            }
        } catch (RuntimeException ignored) { }
        return false;
    }

    /** Android 13+: system dialog «Add tile»; the answer also tells whether the tile is already there. */
    static void requestTile(Activity a) {
        if (Build.VERSION.SDK_INT < 33) return;
        try {
            android.app.StatusBarManager bar = a.getSystemService(android.app.StatusBarManager.class);
            if (bar == null) return;
            android.content.SharedPreferences p = a.getSharedPreferences("replyai_features", 0);
            bar.requestAddTileService(new android.content.ComponentName(a, ReplyTileService.class), "Автоответчик",
                    Icon.createWithResource(a, icon(a)), a.getMainExecutor(), result -> {
                        // 1 already added, 2 added, 0 declined, negative — the system refused the request.
                        p.edit().putInt(TILE_STATE, result).apply();
                        ReplyTileService.requestUpdate(a);
                    });
        } catch (RuntimeException error) {
            Log.w(TAG, "Cannot request tile", error);
        }
    }

    private static void ensureChannel(NotificationManager nm) {
        if (nm.getNotificationChannel(OLD_CHANNEL) != null) nm.deleteNotificationChannel(OLD_CHANNEL);
        if (nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Статус автоответчика",
                NotificationManager.IMPORTANCE_DEFAULT); // visible in the status bar; sound is off below
        channel.setDescription("Постоянное уведомление: статус, отправленные сообщения, включение и остановка");
        channel.setShowBadge(false);
        channel.enableVibration(false);
        channel.setSound(null, null);
        nm.createNotificationChannel(channel);
    }

    private static Notification.Action action(Context c, int icon, String title, String command, int code) {
        Notification.Action.Builder builder = new Notification.Action.Builder(
                Icon.createWithResource(c, icon), I18n.t(c, title), ControlReceiver.pending(c, command, code));
        // On the lock screen the phone must be unlocked first.
        if (Build.VERSION.SDK_INT >= 31) builder.setAuthenticationRequired(true);
        return builder.build();
    }

    static PendingIntent openApp(Context c) {
        Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (open == null) return null;
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Opens «Согласование ответов» directly. */
    static PendingIntent openApprovals(Context c) {
        Intent open = new Intent(c, ApprovalActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 1, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static int icon(Context c) {
        int icon = AutoReplyState.res(c, "replyai_notification_icon", "drawable");
        return icon != 0 ? icon : c.getApplicationInfo().icon;
    }
}
